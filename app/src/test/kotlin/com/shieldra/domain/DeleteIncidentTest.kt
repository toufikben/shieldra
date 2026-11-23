package com.shieldra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P1-4: the event-detail Delete button must really remove the incident, and must leave the other
 * incidents alone. It also has to name the evidence it would orphan, so the caller can delete the
 * payloads it owns instead of leaving unreadable files behind.
 */
class DeleteIncidentTest {
    private fun repositoryWith(vararg events: SecurityEvent): InMemoryEventRepository {
        val repository = InMemoryEventRepository()
        events.forEach { repository.save(it) }
        return repository
    }

    private fun event(type: SecurityEventType = SecurityEventType.PANIC_ACTIVATION) = SecurityEvent.create(
        eventId = EventId.from(java.util.UUID.randomUUID()),
        createdAt = java.time.Instant.parse("2026-11-23T10:00:00Z"),
        severity = GuardSeverity.CRITICAL,
        type = type,
    )

    @Test
    fun deleting_an_incident_removes_its_row_and_reports_its_evidence() {
        val event = event()
        val repository = repositoryWith(event)
        repository.recordEvidence(event.id, EvidenceReference("cap_front", "FRONT_PHOTO"))
        repository.recordEvidence(event.id, EvidenceReference("cap_audio", "AUDIO"))

        val deleted = repository.deleteIncident(event.id)

        assertEquals(setOf("cap_front", "cap_audio"), deleted.evidenceIds)
        assertNull(repository.find(event.id))
    }

    @Test
    fun deleting_one_incident_never_touches_another() {
        val kept = event(SecurityEventType.BATTERY_SIGNAL)
        val deleted = event()
        val repository = repositoryWith(kept, deleted)
        repository.recordEvidence(kept.id, EvidenceReference("kept_front", "FRONT_PHOTO"))

        repository.deleteIncident(deleted.id)

        // `kept` was saved before its evidence reference existed, so the stored row is the one to
        // read: it must still be there and must still hold the reference that was recorded on it.
        val survivor = repository.find(kept.id)
        assertEquals(kept.id, survivor?.id)
        assertEquals(listOf("kept_front"), survivor?.evidenceReferences?.map { it.id })
        assertEquals(listOf(kept.id), repository.listRecent(10).map { it.id })
    }

    @Test
    fun deleting_something_that_is_gone_reports_nothing_to_delete() {
        val repository = InMemoryEventRepository()

        val deleted = repository.deleteIncident(EventId.from(java.util.UUID.randomUUID()))

        assertTrue(deleted.evidenceIds.isEmpty())
        assertTrue(deleted.deleted.not(), "a missing incident must not be reported as deleted")
    }
}
