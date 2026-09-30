package com.shieldra.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class EventId private constructor(val value: UUID) {
    companion object {
        internal fun from(value: UUID): EventId = EventId(value)
    }
}

enum class SecurityEventType {
    CONFIRMED_SECURITY_EVENT,

    /**
     * An event created by an explicit Panic activation (amendment A-7).
     *
     * The distinction exists so a Panic press binds only to a Panic incident.
     * Without it, the "newest non-terminal event" rule would attach a Panic to
     * an unrelated guard event as soon as the next guard starts producing
     * events — the count would keep climbing on someone else's incident.
     */
    PANIC_ACTIVATION,
}

data class EvidenceReference(
    val id: String,
)

enum class DeliveryStatus {
    SUCCESS,
    FAILED,
    DEFERRED,
    SKIPPED,
}

data class DeliveryInformation(
    val channel: String,
    val status: DeliveryStatus,
    val occurredAt: Instant? = null,
    val failureReason: String? = null,
)

class SecurityEvent private constructor(
    val id: EventId,
    val type: SecurityEventType,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val state: EventState,
    val severity: GuardSeverity? = null,
    val evidenceReferences: List<EvidenceReference> = emptyList(),
    val deliveryInformation: List<DeliveryInformation> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    /** How many explicit activations this Panic incident has absorbed (A-7). */
    val panicActivationCount: Int? = null,
    /** When the most recent explicit activation arrived (A-7). */
    val panicLastActivatedAt: Instant? = null,
) {
    companion object {
        internal fun create(
            eventId: EventId,
            createdAt: Instant,
            expiresAt: Instant? = null,
            state: EventState = EventState.INITIATED,
            severity: GuardSeverity? = null,
            evidenceReferences: List<EvidenceReference> = emptyList(),
            deliveryInformation: List<DeliveryInformation> = emptyList(),
            metadata: Map<String, String> = emptyMap(),
            type: SecurityEventType = SecurityEventType.CONFIRMED_SECURITY_EVENT,
            panicActivationCount: Int? = null,
            panicLastActivatedAt: Instant? = null,
        ): SecurityEvent = SecurityEvent(
            id = eventId,
            type = type,
            createdAt = createdAt,
            expiresAt = expiresAt,
            state = state,
            severity = severity,
            evidenceReferences = evidenceReferences.toList(),
            deliveryInformation = deliveryInformation.toList(),
            metadata = metadata.toMap(),
            panicActivationCount = panicActivationCount,
            panicLastActivatedAt = panicLastActivatedAt,
        )
    }

    internal fun withState(
        next: EventState,
        nextMetadata: Map<String, String> = metadata,
        nextExpiresAt: Instant? = expiresAt,
    ): SecurityEvent = create(
        eventId = id,
        createdAt = createdAt,
        expiresAt = nextExpiresAt,
        state = next,
        severity = severity,
        evidenceReferences = evidenceReferences,
        deliveryInformation = deliveryInformation,
        metadata = nextMetadata,
        type = type,
        panicActivationCount = panicActivationCount,
        panicLastActivatedAt = panicLastActivatedAt,
    )
}

/**
 * Durable Panic activation bookkeeping (amendment A-7).
 *
 * Deliberately a separate contract rather than a metadata key on
 * [EventRepository.updateState]: schema v2 had no column for a counter, and
 * loosening the metadata rejection to squeeze one in would have turned a
 * fail-closed boundary into a lossy one. Storage that can hold the counter
 * implements this; the coordinator falls back to a session tally when it
 * cannot, which is honest but not durable.
 *
 * Both operations must be atomic in the storage implementation — a
 * read-modify-write in Kotlin would lose increments under concurrency and
 * could leave a crash between the read and the write.
 */
interface PanicActivationStore {
    /** The count currently persisted for [eventId], or null when it is not a Panic activation. */
    fun panicActivationCount(eventId: EventId): Int?

    /**
     * Atomically increments the persisted count, stamps [at] as the latest
     * activation, and returns the resulting count. A terminal event must be
     * refused, not incremented.
     */
    fun recordPanicActivation(eventId: EventId, at: Instant): RepositoryResult
}
