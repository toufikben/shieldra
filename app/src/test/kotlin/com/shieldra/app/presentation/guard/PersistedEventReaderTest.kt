package com.shieldra.app.presentation.guard

import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistedEventReaderTest {
    private val now = Instant.parse("2026-09-29T10:00:00Z")

    @Test
    fun storage_not_installed_yields_no_events() {
        val reader = PersistedEventReader(storageInstalled = false, loadRecent = { error("must not read") })

        val events = reader.readRecent(limit = 20, now = now)

        assertTrue(events.isEmpty(), "A build without installed storage must show no events")
    }

    @Test
    fun load_failure_yields_no_events_instead_of_throwing() {
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { error("keystore read failed") })

        val events = reader.readRecent(limit = 20, now = now)

        assertTrue(events.isEmpty(), "A failed read must degrade to no events, never crash or fabricate")
    }

    @Test
    fun persisted_events_are_projected_in_the_order_they_were_loaded() {
        val older = event(createdAt = now.minus(Duration.ofHours(2)), state = EventState.DELIVERED)
        val newer = event(createdAt = now.minus(Duration.ofMinutes(5)), state = EventState.INITIATED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(newer, older) })

        val events = reader.readRecent(limit = 20, now = now)

        assertEquals(listOf(newer.id.value.toString(), older.id.value.toString()), events.map { it.id })
    }

    @Test
    fun projection_keeps_the_real_event_type_and_never_invents_photo_or_location() {
        val persisted = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.DELIVERED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(persisted) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(com.shieldra.app.presentation.model.EventType.Panic, projected.type)
        assertTrue(!projected.hasPhoto, "No photo was captured; the projection must not claim one")
        assertTrue(!projected.hasLocation, "No location was captured; the projection must not claim one")
        assertTrue(projected.title.isNotBlank(), "The title must be real text, not blank")
        assertTrue(projected.subtitle.isNotBlank(), "The subtitle must be real text, not blank")
        assertTrue(projected.timestampLabel.isNotBlank(), "The timestamp must be real text, not blank")
    }

    @Test
    fun delivery_state_is_projected_from_the_persisted_event_state() {
        val delivered = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.DELIVERED)
        val failed = event(createdAt = now.minus(Duration.ofMinutes(2)), state = EventState.FAILED_FINAL)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(delivered, failed) })

        val events = reader.readRecent(limit = 20, now = now)

        assertEquals(
            com.shieldra.app.presentation.model.DeliveryStatus.Delivered,
            events[0].deliveryStatus,
        )
        assertEquals(
            com.shieldra.app.presentation.model.DeliveryStatus.Failed,
            events[1].deliveryStatus,
        )
    }

    @Test
    fun the_limit_is_passed_through_to_the_loader() {
        var requested = -1
        val reader = PersistedEventReader(
            storageInstalled = true,
            loadRecent = { limit -> requested = limit; emptyList() },
        )

        reader.readRecent(limit = 3, now = now)

        assertEquals(3, requested, "The caller's limit must reach the storage query")
    }

    @Test
    fun age_is_measured_from_the_real_created_at_not_from_now() {
        val ninetyMinutesAgo = event(createdAt = now.minus(Duration.ofMinutes(90)), state = EventState.READY)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(ninetyMinutesAgo) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals("1h 30m ago", projected.timestampLabel)
    }

    @Test
    fun a_single_event_can_be_looked_up_by_its_real_id() {
        val target = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.DELIVERED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(target) })

        val found = reader.readOne(target.id.value.toString(), now = now)

        assertNotNull(found, "A persisted event must be reachable by its own id")
        assertEquals(target.id.value.toString(), found.id)
    }

    @Test
    fun an_unknown_id_projects_nothing_rather_than_a_placeholder() {
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { emptyList() })

        assertEquals(null, reader.readOne("evt_missing", now = now))
    }

    @Test
    fun every_projected_event_keeps_the_domain_type_constant() {
        val persisted = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.READY)
        assertEquals(SecurityEventType.CONFIRMED_SECURITY_EVENT, persisted.type)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(persisted) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertTrue(projected.id.isNotBlank())
    }

    private fun event(createdAt: Instant, state: EventState): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = createdAt,
        state = state,
    )
}
