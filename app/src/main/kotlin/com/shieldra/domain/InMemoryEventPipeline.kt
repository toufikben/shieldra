package com.shieldra.domain

import java.time.Clock as JavaClock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryEventRepository : EventRepository {
    private val events = mutableMapOf<EventId, SecurityEvent>()

    @Synchronized
    override fun find(eventId: EventId): SecurityEvent? = events[eventId]

    @Synchronized
    override fun save(event: SecurityEvent): RepositoryResult =
        if (events.putIfAbsent(event.id, event) == null) {
            RepositoryResult.Applied
        } else {
            RepositoryResult.AlreadyExists
        }

    @Synchronized
    override fun updateState(
        eventId: EventId,
        expected: EventState,
        next: EventState,
        metadata: Map<String, String>?,
        expiresAt: Instant? = null,
    ): RepositoryResult {
        val current = events[eventId] ?: return RepositoryResult.NotFound
        if (current.state != expected) return RepositoryResult.Conflict(current.state)
        return when (val result = transition(expected, next)) {
            is StateTransitionResult.Rejected -> RepositoryResult.Rejected(result.reason)
            is StateTransitionResult.Allowed -> {
                events[eventId] = current.withState(
                    result.state,
                    metadata ?: current.metadata,
                    expiresAt ?: current.expiresAt,
                )
                RepositoryResult.Applied
            }
        }
    }
}

class InMemoryEventPipeline(
    private val repository: EventRepository,
    private val evidence: EvidenceStage = EvidenceStage { StageOutcome.Completed },
    private val delivery: DeliveryStage = DeliveryStage { StageOutcome.Completed },
    private val now: () -> Instant = { Instant.now(JavaClock.systemUTC()) },
) : EventPipeline {
    private val eventLocks = ConcurrentHashMap<EventId, Any>()

    override fun process(eventId: EventId, expiration: EventExpirationPolicy): PipelineResult {
        val lock = eventLocks.computeIfAbsent(eventId) { Any() }
        return synchronized(lock) {
            val event = repository.find(eventId)
                ?: return@synchronized PipelineResult.Rejected(
                    PipelineFailure(PipelineFailureKind.INVALID_STATE, "Event does not exist"),
                )

            val effectiveExpiresAt = event.expiresAt ?: expiration.expiresAt
            if (event.state != EventState.DELIVERED &&
                event.state != EventState.FAILED_FINAL &&
                event.state != EventState.EXPIRED &&
                effectiveExpiresAt != null && !now().isBefore(effectiveExpiresAt)
            ) {
                return@synchronized expire(event, effectiveExpiresAt)
            }

            when (event.state) {
                EventState.INITIATED -> advance(event, EventState.COLLECTING, PipelineStage.CONFIRMED, expiration.expiresAt)
                EventState.COLLECTING -> runEvidence(event, expiration.expiresAt)
                EventState.READY -> runDelivery(event, expiration.expiresAt)
                EventState.DELIVERING -> alreadyProcessed(event, PipelineStage.DELIVERY)
                EventState.DELIVERED -> alreadyProcessed(event, PipelineStage.COMPLETED)
                EventState.DEFERRED -> resume(event, expiration.expiresAt)
                EventState.FAILED_FINAL -> PipelineResult.Failed(
                    event,
                    PipelineFailure(PipelineFailureKind.INVALID_STATE, "Event reached a terminal failure state"),
                )
                EventState.EXPIRED -> PipelineResult.Expired(event)
            }
        }
    }

    private fun resume(event: SecurityEvent, expiresAt: Instant?): PipelineResult = when {
        event.metadata["deferredStage"] == PipelineStage.DELIVERY.name -> runDelivery(event, expiresAt)
        else -> runEvidence(event, expiresAt)
    }

    private fun runEvidence(event: SecurityEvent, expiresAt: Instant? = null): PipelineResult {
        return when (val outcome = evidence.execute(event)) {
            StageOutcome.Completed -> {
                val updated = transitionAndRead(event, EventState.READY, expiresAt)
                updated?.let { PipelineResult.Advanced(it, PipelineStage.EVIDENCE) }
                    ?: PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.INVALID_STATE, "Evidence state update conflicted"))
            }
            StageOutcome.Deferred -> defer(event, PipelineStage.EVIDENCE)
            is StageOutcome.Failed -> fail(event, PipelineFailure(PipelineFailureKind.EVIDENCE_VALIDATION, outcome.message))
        }
    }

    private fun runDelivery(event: SecurityEvent, expiresAt: Instant? = null): PipelineResult {
        val delivering = transitionAndRead(event, EventState.DELIVERING, expiresAt)
            ?: return PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.INVALID_STATE, "Delivery state update conflicted"))
        return when (val outcome = delivery.execute(delivering)) {
            StageOutcome.Completed -> {
                val completed = transitionAndRead(delivering, EventState.DELIVERED, expiresAt)
                completed?.let { PipelineResult.Advanced(it, PipelineStage.COMPLETED) }
                    ?: PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.INVALID_STATE, "Completion state update conflicted"))
            }
            StageOutcome.Deferred -> defer(delivering, PipelineStage.DELIVERY)
            is StageOutcome.Failed -> fail(delivering, PipelineFailure(PipelineFailureKind.DELIVERY, outcome.message))
        }
    }

    private fun advance(event: SecurityEvent, next: EventState, stage: PipelineStage, expiresAt: Instant? = null): PipelineResult {
        val updated = transitionAndRead(event, next, expiresAt)
            ?: return PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.INVALID_STATE, "State update conflicted"))
        return PipelineResult.Advanced(updated, stage)
    }

    private fun transitionAndRead(event: SecurityEvent, next: EventState, expiresAt: Instant? = null): SecurityEvent? {
        val result = repository.updateState(event.id, event.state, next, expiresAt = expiresAt)
        return if (result is RepositoryResult.Applied) {
            repository.find(event.id)
        } else null
    }

    private fun defer(event: SecurityEvent, stage: PipelineStage): PipelineResult {
        val result = repository.updateState(
            event.id,
            event.state,
            EventState.DEFERRED,
            event.metadata + ("deferredStage" to stage.name),
            expiresAt = event.expiresAt,
        )
        if (result !is RepositoryResult.Applied) {
            return PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.INVALID_STATE, "Deferred state update conflicted"))
        }
        val updated = repository.find(event.id) ?: event
        return PipelineResult.Deferred(updated, stage)
    }

    private fun fail(event: SecurityEvent, failure: PipelineFailure): PipelineResult {
        repository.updateState(event.id, event.state, EventState.FAILED_FINAL, expiresAt = event.expiresAt)
        return PipelineResult.Failed(repository.find(event.id) ?: event, failure)
    }

    private fun expire(event: SecurityEvent, expiresAt: Instant?): PipelineResult {
        val result = repository.updateState(event.id, event.state, EventState.EXPIRED, expiresAt = expiresAt)
        return if (result is RepositoryResult.Applied) {
            PipelineResult.Expired(repository.find(event.id) ?: event)
        } else {
            PipelineResult.Rejected(PipelineFailure(PipelineFailureKind.EXPIRED, "Event expiration update conflicted"))
        }
    }

    private fun alreadyProcessed(event: SecurityEvent, stage: PipelineStage): PipelineResult =
        PipelineResult.AlreadyProcessed(event, stage)

    private fun stageFor(state: EventState): PipelineStage = when (state) {
        EventState.INITIATED -> PipelineStage.CONFIRMED
        EventState.COLLECTING -> PipelineStage.EVIDENCE
        EventState.READY, EventState.DELIVERING -> PipelineStage.DELIVERY
        EventState.DELIVERED -> PipelineStage.COMPLETED
        EventState.DEFERRED -> PipelineStage.EVIDENCE
        EventState.FAILED_FINAL, EventState.EXPIRED -> PipelineStage.COMPLETED
    }
}
