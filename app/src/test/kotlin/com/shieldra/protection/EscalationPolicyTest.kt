package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscalationPolicyTest {

    private val w = EscalationPolicy.WINDOW_MS

    @Test
    fun `a single intrusion never escalates`() {
        assertFalse(EscalationPolicy.shouldEscalate(listOf(1_000L), nowMs = 1_000L, threshold = 3))
    }

    @Test
    fun `reaching the threshold inside the window escalates`() {
        val now = 600_000L
        val times = listOf(now - 120_000L, now - 60_000L, now)
        assertTrue(EscalationPolicy.shouldEscalate(times, now, threshold = 3))
    }

    @Test
    fun `intrusions older than the window do not count`() {
        val now = 2_000_000L
        val times = listOf(now - (w + 1), now - (w + 2), now)
        assertFalse(EscalationPolicy.shouldEscalate(times, now, threshold = 3))
    }

    @Test
    fun `a threshold below the minimum is floored to two`() {
        val now = 100_000L
        // threshold 1 would escalate on a single intrusion; the floor of 2 prevents that.
        assertFalse(EscalationPolicy.shouldEscalate(listOf(now), now, threshold = 1))
        assertTrue(EscalationPolicy.shouldEscalate(listOf(now - 10L, now), now, threshold = 1))
    }

    @Test
    fun `future timestamps are ignored`() {
        val now = 500_000L
        val times = listOf(now + 10_000L, now + 20_000L, now)
        assertFalse(EscalationPolicy.shouldEscalate(times, now, threshold = 3))
    }
}
