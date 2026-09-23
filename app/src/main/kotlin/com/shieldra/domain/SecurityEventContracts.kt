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
        ): SecurityEvent = SecurityEvent(
            id = eventId,
            type = SecurityEventType.CONFIRMED_SECURITY_EVENT,
            createdAt = createdAt,
            expiresAt = expiresAt,
            state = state,
            severity = severity,
            evidenceReferences = evidenceReferences.toList(),
            deliveryInformation = deliveryInformation.toList(),
            metadata = metadata.toMap(),
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
    )
}
