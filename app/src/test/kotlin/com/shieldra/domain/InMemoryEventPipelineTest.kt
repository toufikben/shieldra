package com.shieldra.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryEventPipelineTest {
    private val createdAt = Instant.parse("2026-09-22T20:00:00Z")

    @Test
    fun evidence_stage_that_records_no_artifact_cannot_advance_the_event_to_ready() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = EvidenceStage { StageOutcome.Completed },
            delivery = DeliveryStage { StageOutcome.Completed },
        )

        pipeline.process(event.id)
        val result = pipeline.process(event.id)

        assertIs<PipelineResult.Deferred>(result)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)
        assertTrue(
            repository.find(event.id)!!.evidenceReferences.isEmpty(),
            "Evidence was never collected, so the event must not carry an evidence reference",
        )
    }

    @Test
    fun delivery_stage_that_records_no_receipt_cannot_reach_delivered() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = DeliveryStage { StageOutcome.Completed },
        )

        repeat(2) { pipeline.process(event.id) }
        val result = pipeline.process(event.id)

        assertIs<PipelineResult.Deferred>(result)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)
        assertTrue(
            repository.find(event.id)!!.deliveryInformation.isEmpty(),
            "Nothing was sent, so the event must not carry a delivery receipt",
        )
    }

    @Test
    fun reaching_ready_requires_a_persisted_evidence_reference() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = DeliveryStage { StageOutcome.Deferred },
        )

        pipeline.process(event.id)
        val result = pipeline.process(event.id)

        assertIs<PipelineResult.Advanced>(result)
        val stored = assertNotNull(repository.find(event.id))
        assertEquals(EventState.READY, stored.state)
        assertEquals(listOf("ev-${event.id.value}"), stored.evidenceReferences.map { it.id })
    }

    @Test
    fun reaching_delivered_requires_a_persisted_successful_attempt() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = recordingDelivery(repository),
        )

        repeat(3) { pipeline.process(event.id) }
        val result = pipeline.process(event.id)

        assertIs<PipelineResult.AlreadyProcessed>(result)
        val stored = assertNotNull(repository.find(event.id))
        assertEquals(EventState.DELIVERED, stored.state)
        assertEquals(
            listOf(DeliveryStatus.SUCCESS),
            stored.deliveryInformation.map { it.status },
            "DELIVERED is only reachable through a persisted successful delivery attempt",
        )
    }

    @Test
    fun failed_delivery_attempt_keeps_the_event_out_of_delivered() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = DeliveryStage { attempt ->
                repository.recordDeliveryAttempt(
                    attempt.id,
                    DeliveryInformation(
                        channel = "test-channel",
                        status = DeliveryStatus.FAILED,
                        occurredAt = attempt.createdAt,
                        failureReason = "channel rejected the payload",
                    ),
                )
                StageOutcome.Completed
            },
        )

        repeat(2) { pipeline.process(event.id) }
        val result = pipeline.process(event.id)

        assertIs<PipelineResult.Deferred>(result)
        val stored = assertNotNull(repository.find(event.id))
        assertEquals(EventState.DEFERRED, stored.state)
        assertEquals(
            listOf(DeliveryStatus.FAILED),
            stored.deliveryInformation.map { it.status },
            "A recorded failure must stay visible and must never be presented as a delivery",
        )
    }

    @Test
    fun same_event_id_is_processed_without_duplicate_final_delivery() {
        val repository = InMemoryEventRepository()
        val event = event()
        assertEquals(RepositoryResult.Applied, repository.save(event))
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = recordingDelivery(repository),
        )

        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        val final = pipeline.process(event.id)

        assertIs<PipelineResult.AlreadyProcessed>(final)
        assertEquals(EventState.DELIVERED, repository.find(event.id)?.state)
        assertEquals(RepositoryResult.AlreadyExists, repository.save(event))
    }

    @Test
    fun expiration_is_persisted_on_first_state_update_and_survives_resume() {
        val repository = InMemoryEventRepository()
        val event = event()
        val expiration = Instant.parse("2026-09-23T20:00:00Z")
        repository.save(event)
        val pipeline = pipeline(repository)

        pipeline.process(event.id, EventExpirationPolicy(expiration))

        assertEquals(expiration, repository.find(event.id)?.expiresAt)
        val resumed = repository.find(event.id)!!.withState(EventState.DEFERRED)
        assertEquals(expiration, resumed.expiresAt)
    }

    @Test
    fun delivered_event_with_expired_policy_remains_idempotently_final() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = recordingDelivery(repository),
            now = { Instant.parse("2026-09-22T21:00:00Z") },
        )

        repeat(4) { pipeline.process(event.id) }
        val result = pipeline.process(
            event.id,
            EventExpirationPolicy(Instant.parse("2026-09-22T20:30:00Z")),
        )

        val alreadyProcessed = assertIs<PipelineResult.AlreadyProcessed>(result)
        assertEquals(PipelineStage.COMPLETED, alreadyProcessed.stage)
        assertEquals(EventState.DELIVERED, alreadyProcessed.event.state)
        assertEquals(EventState.DELIVERED, repository.find(event.id)?.state)
    }

    @Test
    fun expired_event_is_distinct_from_failed_final() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = recordingDelivery(repository),
            now = { Instant.parse("2026-09-22T21:00:00Z") },
        )

        val result = pipeline.process(
            event.id,
            EventExpirationPolicy(Instant.parse("2026-09-22T20:30:00Z")),
        )

        assertIs<PipelineResult.Expired>(result)
        assertEquals(EventState.EXPIRED, repository.find(event.id)?.state)
    }

    @Test
    fun deferred_delivery_resumes_from_delivery_stage() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        var attempts = 0
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = DeliveryStage { attempt ->
                attempts += 1
                if (attempts == 1) {
                    StageOutcome.Deferred
                } else {
                    repository.recordDeliveryAttempt(
                        attempt.id,
                        DeliveryInformation(
                            channel = "test-channel",
                            status = DeliveryStatus.SUCCESS,
                            occurredAt = attempt.createdAt,
                        ),
                    )
                    StageOutcome.Completed
                }
            },
        )

        pipeline.process(event.id)
        pipeline.process(event.id)
        val deferred = pipeline.process(event.id)
        assertIs<PipelineResult.Deferred>(deferred)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)

        val resumed = pipeline.process(event.id)
        assertIs<PipelineResult.Advanced>(resumed)
        assertEquals(EventState.DELIVERED, repository.find(event.id)?.state)
        assertEquals(2, attempts)
    }

    @Test
    fun deferred_event_with_unknown_stage_is_rejected_without_guessing_evidence() {
        val repository = InMemoryEventRepository()
        val event = event().withState(
            EventState.DEFERRED,
            mapOf("deferredStage" to "UNKNOWN"),
        )
        repository.save(event)

        val result = pipeline(repository).process(event.id)

        val rejected = assertIs<PipelineResult.Rejected>(result)
        assertEquals(PipelineFailureKind.INVALID_STATE, rejected.failure.kind)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)
    }

    @Test
    fun event_expiring_during_evidence_does_not_advance_to_ready() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        var now = createdAt
        val expiration = createdAt.plusSeconds(30)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = EvidenceStage {
                now = expiration.plusSeconds(1)
                StageOutcome.Completed
            },
            delivery = DeliveryStage { StageOutcome.Deferred },
            now = { now },
        )

        pipeline.process(event.id, EventExpirationPolicy(expiration))
        val result = pipeline.process(event.id, EventExpirationPolicy(expiration))

        assertIs<PipelineResult.Expired>(result)
        assertEquals(EventState.EXPIRED, repository.find(event.id)?.state)
    }

    @Test
    fun failed_stage_is_rejected_when_terminal_failure_cannot_be_persisted() {
        val base = InMemoryEventRepository()
        val repository = object : EventRepository by base {
            override fun updateState(
                eventId: EventId,
                expected: EventState,
                next: EventState,
                metadata: Map<String, String>?,
                expiresAt: Instant?,
            ): RepositoryResult = if (next == EventState.FAILED_FINAL) {
                RepositoryResult.Rejected("injected persistence failure")
            } else {
                base.updateState(eventId, expected, next, metadata, expiresAt)
            }
        }
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = EvidenceStage { StageOutcome.Failed("invalid evidence") },
            delivery = DeliveryStage { StageOutcome.Deferred },
        )

        pipeline.process(event.id)
        val result = pipeline.process(event.id)

        val rejected = assertIs<PipelineResult.Rejected>(result)
        assertEquals(PipelineFailureKind.INVALID_STATE, rejected.failure.kind)
        assertEquals(EventState.COLLECTING, repository.find(event.id)?.state)
    }

    @Test
    fun evidence_failure_becomes_terminal_failure_not_expiration() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = EvidenceStage { StageOutcome.Failed("invalid evidence") },
            delivery = DeliveryStage { StageOutcome.Deferred },
        )

        pipeline.process(event.id)
        val result = pipeline.process(event.id)

        val failed = assertIs<PipelineResult.Failed>(result)
        assertEquals(PipelineFailureKind.EVIDENCE_VALIDATION, failed.failure.kind)
        assertEquals(EventState.FAILED_FINAL, repository.find(event.id)?.state)
    }

    @Test
    fun independent_events_have_independent_state() {
        val repository = InMemoryEventRepository()
        val first = event()
        val second = event()
        repository.save(first)
        repository.save(second)
        val pipeline = pipeline(repository)

        pipeline.process(first.id)
        pipeline.process(second.id)

        assertEquals(EventState.COLLECTING, repository.find(first.id)?.state)
        assertEquals(EventState.COLLECTING, repository.find(second.id)?.state)
        assertTrue(first.id != second.id)
        assertNotNull(repository.find(first.id))
    }

    /** A pipeline whose stages only run when the test needs a stage to run. */
    private fun pipeline(repository: EventRepository): InMemoryEventPipeline = InMemoryEventPipeline(
        repository = repository,
        evidence = EvidenceStage { StageOutcome.Deferred },
        delivery = DeliveryStage { StageOutcome.Deferred },
    )

    /** A stage that does the work and persists the artifact the work produced. */
    private fun recordingEvidence(repository: EventRepository): EvidenceStage = EvidenceStage { event ->
        check(
            repository.recordEvidence(event.id, EvidenceReference("ev-${event.id.value}")) is RepositoryResult.Applied,
        ) { "Test evidence reference could not be persisted" }
        StageOutcome.Completed
    }

    private fun recordingDelivery(repository: EventRepository): DeliveryStage = DeliveryStage { event ->
        check(
            repository.recordDeliveryAttempt(
                event.id,
                DeliveryInformation(
                    channel = "test-channel",
                    status = DeliveryStatus.SUCCESS,
                    occurredAt = event.createdAt,
                ),
            ) is RepositoryResult.Applied,
        ) { "Test delivery attempt could not be persisted" }
        StageOutcome.Completed
    }

    private fun event(): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = createdAt,
        severity = GuardSeverity.HIGH,
    )
}
