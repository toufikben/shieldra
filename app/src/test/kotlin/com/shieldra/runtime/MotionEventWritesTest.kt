package com.shieldra.runtime

import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.MotionConfirmation
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G5 truthfulness fix B: a Motion event the monitor actually wrote must invalidate the event lists
 * in the same process, so the owner sees it without restarting the app. The token is a counter and
 * nothing more — no bus, no polling, no observer framework, and no change to what gets persisted.
 */
class MotionEventWritesTest {

    @Test
    fun the_token_is_a_monotonic_count_visible_without_a_restart() {
        val token: StateFlow<Long> = MotionEventWrites.state

        assertTrue(token.value >= 0L, "The reload token must be readable the moment the UI composes")
    }

    @Test
    fun a_newly_written_motion_event_raises_the_reload_token() {
        val before = MotionEventWrites.state.value

        MotionEventWrites.record(confirmation(SecurityEventType.MOTION_CONFIRMED_EVENT).recorded())

        assertEquals(before + 1L, MotionEventWrites.state.value, "A row was written, so the lists are stale")
    }

    @Test
    fun a_movement_that_joined_the_open_incident_writes_no_row_so_the_token_stays() {
        val before = MotionEventWrites.state.value

        MotionEventWrites.record(confirmation(SecurityEventType.MOTION_CONFIRMED_EVENT).boundToActive())

        assertEquals(before, MotionEventWrites.state.value, "Nothing changed in storage, so nothing must reload")
    }

    @Test
    fun a_signal_or_a_refused_write_persists_nothing_so_the_token_stays() {
        val before = MotionEventWrites.state.value

        MotionEventWrites.record(MotionConfirmation.NotConfirmed("below the confirmation policy"))
        MotionEventWrites.record(MotionConfirmation.Rejected("persistence conflicted"))

        assertEquals(before, MotionEventWrites.state.value, "A confirmation that stored no event may not claim a new one")
    }

    private fun confirmation(type: SecurityEventType): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = Instant.parse("2026-10-01T10:00:00Z"),
        state = EventState.INITIATED,
        type = type,
    )

    private fun SecurityEvent.recorded(): MotionConfirmation = MotionConfirmation.Recorded(this)

    private fun SecurityEvent.boundToActive(): MotionConfirmation = MotionConfirmation.BoundToActive(this)
}
