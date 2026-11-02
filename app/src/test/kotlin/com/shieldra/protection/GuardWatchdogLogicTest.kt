package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit development أ: the patrol that decides whether the guards have gone quiet. These are the cases
 * an owner's phone cannot be asked to produce on demand — a service killed by the battery manager, a
 * silence already reported, a clock moved backwards.
 */
class GuardWatchdogLogicTest {
    private val window = 30L * 60_000L

    @Test
    fun a_check_stamped_inside_the_window_is_alive() {
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = 1_000_000L,
            lastReportedEpochMs = 0L,
            nowEpochMs = 1_000_000L + window - 60_000L,
        )
        assertEquals(GuardWatchdogLogic.Verdict.Watching, verdict)
    }

    @Test
    fun a_silence_exactly_as_long_as_the_window_is_reported_not_watched() {
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = 1_000_000L,
            lastReportedEpochMs = 0L,
            nowEpochMs = 1_000_000L + window,
            toleranceMs = window,
        )
        assertEquals(GuardWatchdogLogic.Verdict.Silent(minutesSilent = 30L, everRan = true), verdict)
    }

    @Test
    /** The window is deliberately generous: ordinary battery throttling must not look like a murder. */
    fun the_default_window_is_at_least_half_an_hour() {
        assertTrue(GuardWatchdogLogic.TOLERANCE_MINUTES >= 30L)
    }

    @Test
    fun a_guard_that_never_stamped_is_silent_but_says_it_never_ran() {
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = 0L,
            lastReportedEpochMs = 0L,
            nowEpochMs = window + 5_000L,
            toleranceMs = window,
        )
        assertTrue(verdict is GuardWatchdogLogic.Verdict.Silent)
        assertFalse((verdict as GuardWatchdogLogic.Verdict.Silent).everRan)
    }

    @Test
    fun one_silence_is_reported_once_however_many_windows_pass() {
        val lastCheck = 1_000_000L
        val reported = lastCheck + window + 60_000L
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = lastCheck,
            lastReportedEpochMs = reported,
            nowEpochMs = reported + window,
            toleranceMs = window,
        )
        assertEquals(GuardWatchdogLogic.Verdict.AlreadyReported, verdict)
    }

    @Test
    fun a_guard_that_stops_again_after_stamping_is_reported_again() {
        val lastCheck = 5_000_000L
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = lastCheck,
            lastReportedEpochMs = lastCheck - window,
            nowEpochMs = lastCheck + window,
            toleranceMs = window,
        )
        assertTrue(verdict is GuardWatchdogLogic.Verdict.Silent)
    }

    @Test
    fun a_clock_moved_backwards_is_not_reported_as_silence() {
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = 10_000_000L,
            lastReportedEpochMs = 0L,
            nowEpochMs = 9_000_000L,
            toleranceMs = window,
        )
        assertEquals(GuardWatchdogLogic.Verdict.Watching, verdict)
    }

    @Test
    fun the_duration_is_the_measured_gap_not_the_window() {
        val verdict = GuardWatchdogLogic.decide(
            lastCheckEpochMs = 0L,
            lastReportedEpochMs = 0L,
            nowEpochMs = 3 * window,
            toleranceMs = window,
        ) as GuardWatchdogLogic.Verdict.Silent
        assertEquals(3 * GuardWatchdogLogic.TOLERANCE_MINUTES, verdict.minutesSilent)
    }

    @Test
    fun the_next_check_is_one_window_after_now() {
        assertEquals(
            2_000_000L + window,
            GuardWatchdogLogic.nextFireAt(nowEpochMs = 2_000_000L, toleranceMs = window),
        )
    }
}
