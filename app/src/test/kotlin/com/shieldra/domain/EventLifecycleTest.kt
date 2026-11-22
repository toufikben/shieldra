package com.shieldra.domain

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EventLifecycleTest {
    private val repository = InMemoryEventRepository()
    private val lifecycle = EventLifecycle(repository)
    private val event = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = Instant.parse("2026-11-22T20:00:00Z"),
    ).also { repository.save(it) }

    @Test
    fun capture_with_persisted_evidence_moves_through_collecting_to_ready() {
        lifecycle.beginCapture(event.id)
        assertEquals(EventState.COLLECTING, repository.find(event.id)!!.state)

        repository.recordEvidence(event.id, EvidenceReference("front-photo", "FRONT_PHOTO"))
        lifecycle.finishCapture(event.id)

        assertEquals(EventState.READY, repository.find(event.id)!!.state)
    }

    @Test
    fun empty_capture_is_deferred_instead_of_claiming_ready() {
        lifecycle.beginCapture(event.id)
        lifecycle.finishCapture(event.id)

        val stored = repository.find(event.id)!!
        assertEquals(EventState.DEFERRED, stored.state)
        assertEquals(PipelineStage.EVIDENCE.name, stored.metadata["deferredStage"])
    }

    @Test
    fun text_only_delivery_does_not_invent_evidence() {
        lifecycle.beginDelivery(event.id)

        val stored = repository.find(event.id)!!
        assertEquals(EventState.DELIVERING, stored.state)
        assertEquals(emptyList(), stored.evidenceReferences)
    }

    @Test
    fun delivered_requires_a_persisted_success_receipt() {
        lifecycle.beginDelivery(event.id)
        lifecycle.finishDelivery(event.id, deferred = false)
        assertEquals(EventState.FAILED_FINAL, repository.find(event.id)!!.state)
    }

    @Test
    fun successful_receipt_completes_delivery() {
        lifecycle.beginDelivery(event.id)
        repository.recordDeliveryAttempt(
            event.id,
            DeliveryInformation("EMAIL", DeliveryStatus.SUCCESS, Instant.parse("2026-11-22T20:01:00Z")),
        )
        lifecycle.finishDelivery(event.id, deferred = false)

        assertEquals(EventState.DELIVERED, repository.find(event.id)!!.state)
    }

    @Test
    fun retryable_delivery_defers_at_delivery_and_can_resume() {
        lifecycle.beginDelivery(event.id)
        lifecycle.finishDelivery(event.id, deferred = true)

        val deferred = repository.find(event.id)!!
        assertEquals(EventState.DEFERRED, deferred.state)
        assertEquals(PipelineStage.DELIVERY.name, deferred.metadata["deferredStage"])

        lifecycle.beginDelivery(event.id)
        assertEquals(EventState.DELIVERING, repository.find(event.id)!!.state)
    }

    @Test
    fun refused_transition_is_reported_and_does_not_start_delivery() {
        val refusingRepository = object : EventRepository by repository {
            override fun updateState(
                eventId: EventId,
                expected: EventState,
                next: EventState,
                metadata: Map<String, String>?,
                expiresAt: Instant?,
            ): RepositoryResult = RepositoryResult.Rejected("injected transition failure")
        }

        assertFalse(EventLifecycle(refusingRepository).beginDelivery(event.id))
        assertEquals(EventState.INITIATED, repository.find(event.id)!!.state)
    }

    @Test
    fun terminal_event_is_never_reopened() {
        lifecycle.beginDelivery(event.id)
        lifecycle.finishDelivery(event.id, deferred = false)
        assertEquals(EventState.FAILED_FINAL, repository.find(event.id)!!.state)

        lifecycle.beginCapture(event.id)
        lifecycle.beginDelivery(event.id)

        assertEquals(EventState.FAILED_FINAL, repository.find(event.id)!!.state)
    }
}
