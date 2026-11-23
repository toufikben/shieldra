package com.shieldra.protection

import com.shieldra.domain.EventRepository
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.InMemoryEventRepository
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidenceAttachmentTest {
    private val repository = InMemoryEventRepository()
    private val event = SecurityEvent.create(
        createdAt = Instant.parse("2026-11-23T00:00:00Z"),
        severity = GuardSeverity.CRITICAL,
        type = SecurityEventType.PANIC_ACTIVATION,
    ).also { repository.save(it) }
    private val reference = EvidenceReference("cap_front", "FRONT_PHOTO")

    @Test
    fun post_insert_callback_failure_is_attached_not_an_orphan() {
        val state = classifyEvidenceAttachment(
            repository = repository,
            eventId = event.id,
            reference = reference,
            record = {
                repository.recordEvidence(event.id, reference)
                error("injected post-insert callback failure")
            },
        )

        assertEquals(EvidenceAttachmentState.ATTACHED, state)
        assertEquals(listOf(reference), repository.find(event.id)!!.evidenceReferences)
    }

    @Test
    fun failed_insert_with_no_persisted_reference_is_a_confirmed_orphan() {
        val state = classifyEvidenceAttachment(
            repository = repository,
            eventId = event.id,
            reference = reference,
            record = { RepositoryResult.Rejected("injected insert failure") },
        )

        assertEquals(EvidenceAttachmentState.ORPHAN, state)
    }

    @Test
    fun unreadable_repository_makes_attachment_unknown_and_forbids_deletion() {
        val unreadable = object : EventRepository by repository {
            override fun find(eventId: com.shieldra.domain.EventId): SecurityEvent? =
                error("injected read failure")
        }

        val state = classifyEvidenceAttachment(
            repository = unreadable,
            eventId = event.id,
            reference = reference,
            record = { RepositoryResult.Rejected("injected uncertain write") },
        )

        assertEquals(EvidenceAttachmentState.UNKNOWN, state)
    }
}
