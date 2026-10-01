package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.LocationKind
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
import kotlin.test.assertNull
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

        assertEquals(
            com.shieldra.app.presentation.model.EventType.Other,
            projected.type,
            "An event of no named guard must not borrow another guard's type",
        )
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

        assertEquals("1h ago", projected.timestampLabel)
    }

    @Test
    fun a_single_event_can_be_looked_up_by_its_real_id() {
        val other = event(createdAt = now.minus(Duration.ofMinutes(3)), state = EventState.READY)
        val target = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.DELIVERED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(other, target) })

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

    @Test
    fun a_clock_skew_into_the_future_never_projects_a_negative_age() {
        val future = event(createdAt = now.plus(Duration.ofMinutes(5)), state = EventState.READY)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(future) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals("0s ago", projected.timestampLabel, "A skewed clock must not produce a negative age")
    }

    @Test
    fun an_injected_localized_formatter_is_used_verbatim() {
        val persisted = event(createdAt = now.minus(Duration.ofMinutes(90)), state = EventState.READY)
        val reader = PersistedEventReader(
            storageInstalled = true,
            loadRecent = { listOf(persisted) },
            labels = PersistedEventLabels(
                title = "حدث أمني",
                stateLabels = mapOf(EventState.READY to "جاهز"),
                ageLabel = { _, _ -> "قبل 1 ساعة" },
            ),
        )

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals("حدث أمني", projected.title)
        assertEquals("جاهز", projected.subtitle)
        assertEquals("قبل 1 ساعة", projected.timestampLabel)
    }

    @Test
    fun a_panic_that_collected_nothing_is_never_projected_as_evidence() {
        val persisted = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.INITIATED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(persisted) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertTrue(!projected.hasPhoto, "No evidence was collected, so history must not claim a photo")
        assertTrue(!projected.hasLocation, "No evidence was collected, so history must not claim a location")
        assertEquals(
            com.shieldra.app.presentation.model.DeliveryStatus.Skipped,
            projected.deliveryStatus,
            "An event that never entered delivery must not be projected as sending",
        )
        assertEquals(EventState.INITIATED.name, projected.subtitle)
    }

    @Test
    fun a_panic_that_was_never_sent_projects_no_delivery_receipt() {
        val persisted = event(createdAt = now.minus(Duration.ofMinutes(1)), state = EventState.INITIATED)
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(persisted) })

        val detail = assertNotNull(reader.readOne(persisted.id.value.toString(), now = now))

        assertTrue(detail.delivery.isEmpty(), "Nothing was attempted, so the detail must show no receipt")
        assertTrue(!detail.hasPhoto)
        assertNull(detail.batteryPercent, "No battery reading was taken, so the detail must not show one")
        assertEquals(LocationKind.Unavailable, detail.location.kind)
    }

    @Test
    fun states_that_performed_no_delivery_are_not_labelled_as_sending_or_delivered() {
        listOf(EventState.INITIATED, EventState.COLLECTING, EventState.READY).forEach { state ->
            val label = eventStateLabelRes(state)
            assertTrue(
                label != com.shieldra.app.R.string.status_sending &&
                    label != com.shieldra.app.R.string.status_delivered,
                "$state must not be labelled as a delivery, because no delivery happened",
            )
        }
        assertEquals(com.shieldra.app.R.string.status_delivered, eventStateLabelRes(EventState.DELIVERED))
        assertEquals(com.shieldra.app.R.string.status_sending, eventStateLabelRes(EventState.DELIVERING))
        assertEquals(com.shieldra.app.R.string.status_failed, eventStateLabelRes(EventState.FAILED_FINAL))
        assertEquals(com.shieldra.app.R.string.status_expired, eventStateLabelRes(EventState.EXPIRED))
        assertEquals(com.shieldra.app.R.string.status_retrying, eventStateLabelRes(EventState.DEFERRED))
    }

    // G5 truthfulness fix A: the History type comes from the row's own persisted guard, so a
    // Motion event can never be filed under Panic by the filter, the icon, or the reader's labels.
    @Test
    fun a_persisted_motion_event_projects_as_motion() {
        val motion = event(
            createdAt = now.minus(Duration.ofMinutes(1)),
            state = EventState.INITIATED,
            type = SecurityEventType.MOTION_CONFIRMED_EVENT,
        )
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(motion) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(com.shieldra.app.presentation.model.EventType.Motion, projected.type)
    }

    @Test
    fun a_persisted_panic_activation_projects_as_panic() {
        val panic = event(
            createdAt = now.minus(Duration.ofMinutes(1)),
            state = EventState.INITIATED,
            type = SecurityEventType.PANIC_ACTIVATION,
        )
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(panic) })

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(com.shieldra.app.presentation.model.EventType.Panic, projected.type)
    }

    @Test
    fun every_persisted_guard_projects_to_its_own_history_type_and_none_to_another_s() {
        val rows = SecurityEventType.entries.mapIndexed { index, type ->
            event(
                createdAt = now.minus(Duration.ofMinutes((index + 1).toLong())),
                state = EventState.INITIATED,
                type = type,
            )
        }
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { rows })

        val projected = reader.readRecent(limit = 20, now = now).associateBy { it.id }

        rows.forEach { row ->
            assertEquals(
                persistedEventTypeOf(row.type),
                projected.getValue(row.id.value.toString()).type,
                "A ${row.type} row must carry the history type its own guard names",
            )
        }
        assertEquals(
            SecurityEventType.entries.size,
            projected.values.map { it.type }.distinct().size,
            "Two different persisted guards must not share one history type",
        )
    }

    @Test
    fun a_reader_wide_label_can_never_retype_a_row() {
        val motion = event(
            createdAt = now.minus(Duration.ofMinutes(1)),
            state = EventState.INITIATED,
            type = SecurityEventType.MOTION_CONFIRMED_EVENT,
        )
        val reader = PersistedEventReader(
            storageInstalled = true,
            loadRecent = { listOf(motion) },
            labels = PersistedEventLabels(title = "حدث أمني"),
        )

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(com.shieldra.app.presentation.model.EventType.Motion, projected.type)
        assertEquals("حدث أمني", projected.title)
    }

    @Test
    fun the_event_detail_keeps_the_same_guard_type_as_the_list() {
        val motion = event(
            createdAt = now.minus(Duration.ofMinutes(1)),
            state = EventState.INITIATED,
            type = SecurityEventType.MOTION_CONFIRMED_EVENT,
        )
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(motion) })

        val detail = assertNotNull(reader.readOne(motion.id.value.toString(), now = now))

        assertEquals(com.shieldra.app.presentation.model.EventType.Motion, detail.type)
    }

    private fun event(
        createdAt: Instant,
        state: EventState,
        type: SecurityEventType = SecurityEventType.CONFIRMED_SECURITY_EVENT,
    ): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = createdAt,
        state = state,
        type = type,
    )
}
