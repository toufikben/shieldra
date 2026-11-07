package com.shieldra.protection

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A-33: the threshold rule that decides when a wrong unlock becomes a captured, alerted attempt. */
class FailedUnlockCounterTest {
    private val start = Instant.parse("2026-10-13T08:00:00Z")

    @Test
    fun below_the_threshold_nothing_is_captured_then_the_threshold_attempt_captures() {
        val counter = FailedUnlockCounter(threshold = { 2 })

        assertEquals(FailedUnlockCounter.Verdict.BelowThreshold(1), counter.onFailure(start))
        assertEquals(FailedUnlockCounter.Verdict.Capture(2), counter.onFailure(start.plusSeconds(3)))
    }

    @Test
    fun every_attempt_after_the_threshold_keeps_capturing() {
        val counter = FailedUnlockCounter(threshold = { 2 })
        counter.onFailure(start)
        counter.onFailure(start.plusSeconds(3))

        assertIs<FailedUnlockCounter.Verdict.Capture>(counter.onFailure(start.plusSeconds(6)))
        assertEquals(4, (counter.onFailure(start.plusSeconds(9)) as FailedUnlockCounter.Verdict.Capture).count)
    }

    @Test
    fun a_correct_unlock_resets_the_session() {
        val counter = FailedUnlockCounter(threshold = { 2 })
        counter.onFailure(start)
        counter.onSuccess()

        assertEquals(FailedUnlockCounter.Verdict.BelowThreshold(1), counter.onFailure(start.plusSeconds(5)))
    }

    @Test
    fun a_long_quiet_gap_starts_a_fresh_session() {
        val counter = FailedUnlockCounter(threshold = { 2 }, sessionGap = Duration.ofMinutes(10))
        counter.onFailure(start)

        // 11 minutes later is a new session, so this is attempt 1, not 2.
        assertEquals(FailedUnlockCounter.Verdict.BelowThreshold(1), counter.onFailure(start.plusSeconds(660)))
    }

    @Test
    fun a_threshold_of_one_captures_on_the_first_attempt() {
        val counter = FailedUnlockCounter(threshold = { 1 })

        assertEquals(FailedUnlockCounter.Verdict.Capture(1), counter.onFailure(start))
    }

    @Test
    fun the_threshold_is_clamped_to_the_allowed_range() {
        val tooHigh = FailedUnlockCounter(threshold = { 99 })
        repeat(4) { tooHigh.onFailure(start.plusSeconds(it.toLong())) }
        // Clamped to 5: the fifth attempt is the first capture.
        assertIs<FailedUnlockCounter.Verdict.Capture>(tooHigh.onFailure(start.plusSeconds(4)))
    }
}
