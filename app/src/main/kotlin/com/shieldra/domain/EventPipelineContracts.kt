package com.shieldra.domain

import java.time.Instant

interface EventRepository {
    fun find(eventId: EventId): SecurityEvent?
    fun save(event: SecurityEvent): RepositoryResult

    /**
     * Newest-first page of persisted events. Callers must invoke this off the
     * UI thread; the local Room implementation throws if queried on the main
     * thread.
     */
    fun listRecent(limit: Int): List<SecurityEvent>
    fun updateState(
        eventId: EventId,
        expected: EventState,
        next: EventState,
        metadata: Map<String, String>? = null,
        expiresAt: Instant? = null,
    ): RepositoryResult

    /**
     * Attaches one evidence artifact that a stage really produced. Recording is
     * the only way an event can gain evidence; a pipeline may not advance to a
     * state that implies evidence unless this call succeeded.
     */
    fun recordEvidence(eventId: EventId, reference: EvidenceReference): RepositoryResult

    /**
     * Attaches one delivery attempt that really happened, with its real status.
     * A successful delivery state implies a recorded SUCCESS attempt.
     */
    fun recordDeliveryAttempt(eventId: EventId, information: DeliveryInformation): RepositoryResult
}

sealed interface RepositoryResult {
    data object Applied : RepositoryResult
    data object AlreadyExists : RepositoryResult
    data object NotFound : RepositoryResult
    data class Conflict(val current: EventState) : RepositoryResult
    data class Rejected(val reason: String) : RepositoryResult
}

enum class PipelineStage {
    CONFIRMED,
    EVIDENCE,
    DELIVERY,
    COMPLETED,
}

enum class PipelineFailureKind {
    INVALID_STATE,
    EVIDENCE_VALIDATION,
    DELIVERY,
    EXPIRED,
}

data class PipelineFailure(
    val kind: PipelineFailureKind,
    val message: String,
)

sealed interface PipelineResult {
    data class Advanced(
        val event: SecurityEvent,
        val stage: PipelineStage,
    ) : PipelineResult

    data class AlreadyProcessed(
        val event: SecurityEvent,
        val stage: PipelineStage,
    ) : PipelineResult

    data class Deferred(
        val event: SecurityEvent,
        val stage: PipelineStage,
    ) : PipelineResult

    data class Failed(
        val event: SecurityEvent,
        val failure: PipelineFailure,
    ) : PipelineResult

    data class Expired(val event: SecurityEvent) : PipelineResult

    data class Rejected(val failure: PipelineFailure) : PipelineResult
}

data class EventExpirationPolicy(
    val expiresAt: Instant? = null,
)

fun interface EvidenceStage {
    fun execute(event: SecurityEvent): StageOutcome
}

fun interface DeliveryStage {
    fun execute(event: SecurityEvent): StageOutcome
}

sealed interface StageOutcome {
    data object Completed : StageOutcome
    data object Deferred : StageOutcome
    data class Failed(val message: String) : StageOutcome
}
