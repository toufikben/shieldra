package com.shieldra.storage.room

import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import com.shieldra.domain.EventState
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Local Room implementation of the domain repository.
 * The coordinator remains unaware of Room and must call this repository off the UI thread.
 */
class RoomEventRepository(
    private val database: ShieldraDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : EventRepository {
    private val events = database.securityEventDao()
    private val evidence = database.evidenceReferenceDao()
    private val delivery = database.deliveryAttemptDao()

    override fun find(eventId: EventId): SecurityEvent? = events.find(eventId.value.toString())?.let(::toDomain)

    override fun save(event: SecurityEvent): RepositoryResult {
        val entity = event.toEntity(now = clock.instant())
        var inserted = false
        try {
            database.runInTransaction {
                inserted = events.insert(entity) != -1L
                if (inserted) {
                    event.evidenceReferences.forEach { reference ->
                        check(
                            evidence.insert(
                                EvidenceReferenceEntity(
                                    evidenceId = reference.id,
                                    eventId = event.id.value.toString(),
                                    kind = "UNSPECIFIED",
                                    contentReference = null,
                                    capturedAtEpochMs = null,
                                ),
                            ) != -1L,
                        ) { "Evidence reference collision: ${reference.id}" }
                    }
                    event.deliveryInformation.forEachIndexed { index, information ->
                        check(
                            delivery.insert(information.toEntity(event.id.value.toString(), index)) != -1L,
                        ) { "Delivery attempt collision: ${event.id.value}:$index" }
                    }
                }
            }
        } catch (error: IllegalStateException) {
            return RepositoryResult.Rejected(error.message ?: "Child row persistence failed")
        }
        return if (inserted) RepositoryResult.Applied else RepositoryResult.AlreadyExists
    }

    override fun updateState(
        eventId: EventId,
        expected: EventState,
        next: EventState,
        metadata: Map<String, String>?,
        expiresAt: Instant?,
    ): RepositoryResult {
        val current = events.find(eventId.value.toString()) ?: return RepositoryResult.NotFound
        if (current.state != expected.name) return RepositoryResult.Conflict(EventState.valueOf(current.state))
        val transition = com.shieldra.domain.transition(expected, next)
        if (transition is com.shieldra.domain.StateTransitionResult.Rejected) {
            return RepositoryResult.Rejected(transition.reason)
        }
        val resumeStage = metadata?.get("deferredStage") ?: current.resumeStage
        val changed = events.updateStateIfExpected(
            eventId = eventId.value.toString(),
            expectedState = expected.name,
            nextState = next.name,
            updatedAtEpochMs = clock.millis(),
            resumeStage = resumeStage,
            expiresAtEpochMs = expiresAt?.toEpochMilli() ?: current.expiresAtEpochMs,
        )
        return if (changed == 1) RepositoryResult.Applied
        else events.find(eventId.value.toString())?.let { RepositoryResult.Conflict(EventState.valueOf(it.state)) }
            ?: RepositoryResult.NotFound
    }

    private fun toDomain(entity: SecurityEventEntity): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.fromString(entity.eventId)),
        createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
        expiresAt = entity.expiresAtEpochMs?.let(Instant::ofEpochMilli),
        state = EventState.valueOf(entity.state),
        severity = entity.severity?.let(GuardSeverity::valueOf),
        evidenceReferences = evidence.findForEvent(entity.eventId).map { com.shieldra.domain.EvidenceReference(it.evidenceId) },
        deliveryInformation = delivery.findForEvent(entity.eventId).map { attempt ->
            DeliveryInformation(
                channel = attempt.channel,
                status = com.shieldra.domain.DeliveryStatus.valueOf(attempt.status),
                occurredAt = attempt.occurredAtEpochMs?.let(Instant::ofEpochMilli),
                failureReason = attempt.failureReason,
            )
        },
        metadata = entity.resumeStage?.let { mapOf("deferredStage" to it) } ?: emptyMap(),
    )

    private fun SecurityEvent.toEntity(now: Instant): SecurityEventEntity = SecurityEventEntity(
        eventId = id.value.toString(),
        eventType = type.name,
        state = state.name,
        severity = severity?.name,
        createdAtEpochMs = createdAt.toEpochMilli(),
        updatedAtEpochMs = now.toEpochMilli(),
        resumeStage = metadata["deferredStage"],
        expiresAtEpochMs = expiresAt?.toEpochMilli(),
    )

    private fun DeliveryInformation.toEntity(eventId: String, index: Int): DeliveryAttemptEntity =
        DeliveryAttemptEntity(
            attemptId = "$eventId:$index",
            eventId = eventId,
            channel = channel,
            status = status.name,
            occurredAtEpochMs = occurredAt?.toEpochMilli(),
            failureReason = failureReason,
        )
}
