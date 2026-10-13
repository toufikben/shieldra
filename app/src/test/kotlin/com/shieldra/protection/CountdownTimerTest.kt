package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A-33: the countdown's two-clock deadline, which no clock change can bring forward or push back. */
class CountdownTimerTest {
    @Test
    fun remaining_time_is_the_duration_right_after_start() {
        val d = CountdownDeadline.start(durationMs = 60 * 60_000L, nowEpochMs = 1_000L, nowElapsedMs = 500L)

        assertEquals(60 * 60_000L, d.remainingMs(nowElapsedMs = 500L))
        assertFalse(d.isExpired(500L))
    }

    @Test
    fun a_duration_below_the_floor_is_raised_to_the_minimum() {
        val d = CountdownDeadline.start(durationMs = 1L, nowEpochMs = 0L, nowElapsedMs = 0L)

        assertEquals(CountdownDeadline.MIN_DURATION_MS, d.remainingMs(0L))
    }

    @Test
    fun moving_the_wall_clock_cannot_change_the_firing_because_steady_state_uses_the_monotonic_clock() {
        val d = CountdownDeadline.start(durationMs = 3_600_000L, nowEpochMs = 0L, nowElapsedMs = 0L)

        // Wall clock jumps an hour ahead, but the monotonic clock advanced only a second: not due.
        assertFalse(d.isExpired(nowElapsedMs = 1_000L))
        assertEquals(3_599_000L, d.remainingMs(1_000L))
        // Only the monotonic clock reaching the deadline fires it.
        assertTrue(d.isExpired(nowElapsedMs = 3_600_000L))
    }

    @Test
    fun boot_recovery_uses_the_wall_clock_because_the_monotonic_clock_reset() {
        val d = CountdownDeadline.start(durationMs = 3_600_000L, nowEpochMs = 1_000_000L, nowElapsedMs = 5_000L)

        // After a reboot the wall clock is past the stored epoch deadline: the cycle came due.
        assertTrue(d.isEpochExpired(nowEpochMs = 1_000_000L + 3_600_000L))
        assertFalse(d.isEpochExpired(nowEpochMs = 1_000_000L + 60_000L))
    }

    @Test
    fun the_presets_run_from_one_minute_to_seven_days_in_order() {
        assertEquals(60_000L, CountdownDeadline.PRESETS_MS.first())
        assertEquals(7 * 24 * 60 * 60_000L, CountdownDeadline.PRESETS_MS.last())
        assertEquals(CountdownDeadline.PRESETS_MS.sorted(), CountdownDeadline.PRESETS_MS)
    }
}
