package com.shieldra.protection

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A-45 D1: the shape of the snatch ladder. The first evidence burst waits out the owner-unlock
 * demand, the fast phase runs every minute for five bursts, the slow phase every five minutes, and
 * the whole thing stops at the 30-minute window. Timing only — no device, no Android.
 */
class SnatchLadderTest {

    @Test
    fun `the first burst is the demand window`() {
        assertEquals(30_000L, SnatchLadder.burstAtMs(1))
    }

    @Test
    fun `the fast phase is one minute apart`() {
        assertEquals(90_000L, SnatchLadder.burstAtMs(2))
        assertEquals(330_000L, SnatchLadder.burstAtMs(6))
    }

    @Test
    fun `the slow phase is five minutes apart`() {
        assertEquals(630_000L, SnatchLadder.burstAtMs(7))
        assertEquals(1_530_000L, SnatchLadder.burstAtMs(10))
    }

    @Test
    fun `a burst that would fall past the window is never scheduled`() {
        // burstAtMs(11) = 1_830_000 ms, past the 30-minute window.
        val tick = SnatchLadder.next(elapsedMs = 1_531_000L, burstsDone = 10)
        assertEquals(LadderAction.SETTLED, tick.action)
        assertEquals(0L, tick.delayMs)
    }

    @Test
    fun `elapsed inside the demand window waits for the rest of it`() {
        val tick = SnatchLadder.next(elapsedMs = 12_000L, burstsDone = 0)
        assertEquals(LadderAction.EVIDENCE_BURST, tick.action)
        assertEquals(18_000L, tick.delayMs)
        assertEquals(1, tick.burst)
    }

    @Test
    fun `elapsed past a due burst schedules the next one, not a pile of them`() {
        // 200 s elapsed, three bursts fired: burst 4 is due at 210 s.
        val tick = SnatchLadder.next(elapsedMs = 200_000L, burstsDone = 3)
        assertEquals(LadderAction.EVIDENCE_BURST, tick.action)
        assertEquals(10_000L, tick.delayMs)
        assertEquals(4, tick.burst)
    }

    @Test
    fun `a restarted ladder begins at the demand again`() {
        val tick = SnatchLadder.next(elapsedMs = 0L, burstsDone = 0)
        assertEquals(SnatchLadder.DEMAND_MS, tick.delayMs)
    }

    @Test
    fun `the window is thirty minutes and the demand is thirty seconds`() {
        assertEquals(30_000L, SnatchLadder.DEMAND_MS)
        assertEquals(1_800_000L, SnatchLadder.LADDER_WINDOW_MS)
        assertEquals(5, SnatchLadder.FAST_BURSTS)
    }
}
