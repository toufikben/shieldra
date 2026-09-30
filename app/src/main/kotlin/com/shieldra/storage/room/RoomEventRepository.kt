package com.shieldra.storage.room

import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import com.shieldra.domain.EventState
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import com.shieldra.storage.EvidenceKeyPolicy
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

    override fun listRecent(limit: Int): List<SecurityEvent> {
        require(limit > 0) { "limit must be positive" }
        return events.findRecent(limit).map(::toDomain)
    }

    override fun save(event: SecurityEvent): RepositoryResult {
        val entity = event.toEntity(now = clock.instant())
        var inserted = false
        try {
            database.runInTransaction {
                inserted = events.insert(entity) != -1L
                if (inserted) {
                    event.evidenceReferences.forEach { reference ->
                        check(
                            evidence.insert(reference.toEntity(event.id.value.toString())) != -1L,
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
        val unsupportedKeys = metadata?.keys?.filter { it != DEFERRED_STAGE_KEY }
        if (!unsupportedKeys.isNullOrEmpty()) {
            return RepositoryResult.Rejected("Schema v2 storage persists no metadata keys besides $DEFERRED_STAGE_KEY")
        }
        if (current.state != expected.name) return RepositoryResult.Conflict(EventState.valueOf(current.state))
        val transition = com.shieldra.domain.transition(expected, next)
        if (transition is com.shieldra.domain.StateTransitionResult.Rejected) {
            return RepositoryResult.Rejected(transition.reason)
        }
        val resumeStage = metadata?.get(DEFERRED_STAGE_KEY) ?: current.resumeStage
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

    override fun recordEvidence(eventId: EventId, reference: EvidenceReference): RepositoryResult {
        val id = eventId.value.toString()
        if (events.find(id) == null) return RepositoryResult.NotFound
        val inserted = evidence.insert(reference.toEntity(id))
        return if (inserted != -1L) RepositoryResult.Applied else RepositoryResult.AlreadyExists
    }

    override fun recordDeliveryAttempt(eventId: EventId, information: DeliveryInformation): RepositoryResult {
        val id = eventId.value.toString()
        if (events.find(id) == null) return RepositoryResult.NotFound
        val index = delivery.findForEvent(id).size
        val inserted = delivery.insert(information.toEntity(id, index))
        return if (inserted != -1L) RepositoryResult.Applied else RepositoryResult.Rejected("Delivery attempt collision: $id:$index")
    }

    private fun toDomain(entity: SecurityEventEntity): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.fromString(entity.eventId)),
        createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
        expiresAt = entity.expiresAtEpochMs?.let(Instant::ofEpochMilli),
        state = EventState.valueOf(entity.state),
        severity = entity.severity?.let(GuardSeverity::valueOf),
        evidenceReferences = evidence.findForEvent(entity.eventId).map { EvidenceReference(it.evidenceId) },
        deliveryInformation = delivery.findForEvent(entity.eventId).map { attempt ->
            DeliveryInformation(
                channel = attempt.channel,
                status = DeliveryStatus.valueOf(attempt.status),
                occurredAt = attempt.occurredAtEpochMs?.let(Instant::ofEpochMilli),
                failureReason = attempt.failureReason,
            )
        },
        metadata = entity.resumeStage?.let { mapOf(DEFERRED_STAGE_KEY to it) } ?: emptyMap(),
    )

    private fun SecurityEvent.toEntity(now: Instant): SecurityEventEntity = SecurityEventEntity(
        eventId = id.value.toString(),
        eventType = type.name,
        state = state.name,
        severity = severity?.name,
        createdAtEpochMs = createdAt.toEpochMilli(),
        updatedAtEpochMs = now.toEpochMilli(),
        resumeStage = metadata[DEFERRED_STAGE_KEY],
        expiresAtEpochMs = expiresAt?.toEpochMilli(),
    )

    private fun EvidenceReference.toEntity(eventId: String): EvidenceReferenceEntity = EvidenceReferenceEntity(
        evidenceId = id,
        eventId = eventId,
        kind = "UNSPECIFIED",
        contentReference = null,
        capturedAtEpochMs = null,
        keyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
        aadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
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

    private companion object {
        /** The only metadata key schema v2 has a column for; anything else is refused, not dropped. */
        const val DEFERRED_STAGE_KEY = "deferredStage"
    }
}
