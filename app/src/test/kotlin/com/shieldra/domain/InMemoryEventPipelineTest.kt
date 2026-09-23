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
    fun same_event_id_is_processed_without_duplicate_final_delivery() {
        val repository = InMemoryEventRepository()
        val event = event()
        assertEquals(RepositoryResult.Applied, repository.save(event))
        val pipeline = InMemoryEventPipeline(repository)

        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        assertIs<PipelineResult.Advanced>(pipeline.process(event.id))
        val final = pipeline.process(event.id)

        assertIs<PipelineResult.AlreadyProcessed>(final)
        assertEquals(EventState.DELIVERED, repository.find(event.id)?.state)
        assertEquals(RepositoryResult.AlreadyExists, repository.save(event))
    }

    @Test
    fun delivered_event_with_expired_policy_remains_idempotently_final() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
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
            delivery = DeliveryStage {
                attempts += 1
                if (attempts == 1) StageOutcome.Deferred else StageOutcome.Completed
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
    fun evidence_failure_becomes_terminal_failure_not_expiration() {
        val repository = InMemoryEventRepository()
        val event = event()
        repository.save(event)
        val pipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = EvidenceStage { StageOutcome.Failed("invalid evidence") },
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
        val pipeline = InMemoryEventPipeline(repository)

        pipeline.process(first.id)
        pipeline.process(second.id)

        assertEquals(EventState.COLLECTING, repository.find(first.id)?.state)
        assertEquals(EventState.COLLECTING, repository.find(second.id)?.state)
        assertTrue(first.id != second.id)
        assertNotNull(repository.find(first.id))
    }

    private fun event(): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = createdAt,
        severity = GuardSeverity.HIGH,
    )
}
