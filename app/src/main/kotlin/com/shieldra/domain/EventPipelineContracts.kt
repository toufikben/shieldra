package com.shieldra.domain

import java.time.Instant

interface EventRepository {
    fun find(eventId: EventId): SecurityEvent?
    fun save(event: SecurityEvent): RepositoryResult
    fun updateState(
        eventId: EventId,
        expected: EventState,
        next: EventState,
        metadata: Map<String, String>? = null,
    ): RepositoryResult
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
