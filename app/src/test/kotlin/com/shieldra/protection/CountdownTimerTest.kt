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

        assertEquals(60 * 60_000L, d.remainingMs(nowEpochMs = 1_000L, nowElapsedMs = 500L))
        assertFalse(d.isExpired(1_000L, 500L))
    }

    @Test
    fun a_duration_below_the_floor_is_raised_to_the_minimum() {
        val d = CountdownDeadline.start(durationMs = 1L, nowEpochMs = 0L, nowElapsedMs = 0L)

        assertEquals(CountdownDeadline.MIN_DURATION_MS, d.remainingMs(0L, 0L))
    }

    @Test
    fun moving_the_wall_clock_forward_cannot_make_it_fire_early() {
        val d = CountdownDeadline.start(durationMs = 3_600_000L, nowEpochMs = 0L, nowElapsedMs = 0L)

        // Wall clock jumps an hour ahead, but the monotonic clock advanced only a second.
        assertFalse(d.isExpired(nowEpochMs = 3_600_000L, nowElapsedMs = 1_000L))
        assertEquals(3_599_000L, d.remainingMs(3_600_000L, 1_000L))
    }

    @Test
    fun moving_the_wall_clock_back_cannot_postpone_past_the_real_duration() {
        val d = CountdownDeadline.start(durationMs = 3_600_000L, nowEpochMs = 0L, nowElapsedMs = 0L)

        // Wall clock dragged back, but the monotonic clock really reached the deadline.
        assertTrue(d.isExpired(nowEpochMs = -1_000_000L, nowElapsedMs = 3_600_000L))
    }

    @Test
    fun the_presets_run_from_one_minute_to_seven_days_in_order() {
        assertEquals(60_000L, CountdownDeadline.PRESETS_MS.first())
        assertEquals(7 * 24 * 60 * 60_000L, CountdownDeadline.PRESETS_MS.last())
        assertEquals(CountdownDeadline.PRESETS_MS.sorted(), CountdownDeadline.PRESETS_MS)
    }
}
