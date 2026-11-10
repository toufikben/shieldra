package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The snatch ladder is decided without a device: ten minutes of proof, then full evidence at the
 * owner's cadence, a wrong PIN only as an accelerator, and a phone that slows down before its last
 * percent is spent. The battery case is the owner's 10% agreement, so it is stated here rather than
 * left to a comment in the coordinator.
 */
class SnatchPolicyTest {

    private val start = 1_000_000L

    @Test
    fun `a confirmed snatch begins by waiting for proof, not by capturing`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true)

        assertEquals(SnatchPolicy.Phase.AWAITING_PROOF, incident.phase)
        assertEquals("event-1", incident.eventId)
        assertEquals(start + SnatchPolicy.PROOF_WINDOW_MS, incident.nextDueAtMs)
        assertEquals(start, incident.lockedAtMs)
        assertEquals(0, incident.ticks)
        assertTrue(incident.active)
    }

    @Test
    fun `a lock that was refused is still an incident, so the tick can try again`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = false)

        assertEquals(0L, incident.lockedAtMs)
        assertTrue(incident.active)
    }

    @Test
    fun `no proof after the window escalates on its own`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true)
        val due = start + SnatchPolicy.PROOF_WINDOW_MS
        val escalated = SnatchPolicy.escalate(incident, due, 5 * 60_000L)

        assertEquals(SnatchPolicy.Phase.ESCALATING, escalated.phase)
        assertEquals(1, escalated.ticks)
        assertEquals(due, escalated.escalatedAtMs)
        assertEquals(due + 5 * 60_000L, escalated.nextDueAtMs)
        assertEquals(start, escalated.startedAtMs)
    }

    @Test
    fun `the ladder slows to ten minutes at the battery floor`() {
        // The owner's five minutes, while the phone has power to spare or is on a charger.
        assertEquals(5 * 60_000L, SnatchPolicy.intervalMs(5, 80, charging = false))
        assertEquals(5 * 60_000L, SnatchPolicy.intervalMs(5, 10, charging = true))
        // At the agreed floor, unplugged, the longest step wins so the last percent is not spent on a burst.
        assertEquals(10 * 60_000L, SnatchPolicy.intervalMs(5, 10, charging = false))
        assertEquals(10 * 60_000L, SnatchPolicy.intervalMs(5, 3, charging = false))
        // The owner's ten minutes stays ten when the battery is healthy.
        assertEquals(10 * 60_000L, SnatchPolicy.intervalMs(10, 80, charging = false))
    }

    @Test
    fun `the cadence is clamped to the two offered values`() {
        assertEquals(5 * 60_000L, SnatchPolicy.intervalMs(1, null, charging = false))
        assertEquals(10 * 60_000L, SnatchPolicy.intervalMs(60, null, charging = false))
    }

    @Test
    fun `unlocking with the owner's credential is proof, but not in the first fifteen seconds`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true)

        assertFalse(SnatchPolicy.unlockedSince(incident, start + 14_000L, keyguardLocked = false))
        assertTrue(SnatchPolicy.unlockedSince(incident, start + 16_000L, keyguardLocked = false))
        assertFalse(SnatchPolicy.unlockedSince(incident, start + 60_000L, keyguardLocked = true))
    }

    @Test
    fun `a phone that was never locked cannot prove ownership by being unlocked`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = false)
        assertFalse(SnatchPolicy.unlockedSince(incident, start + 60_000L, keyguardLocked = false))
    }

    @Test
    fun `proof clears the whole incident`() {
        val escalated = SnatchPolicy.escalate(SnatchPolicy.begin("event-1", start, locked = true), start + 600_000L, 300_000L)
        val cleared = SnatchPolicy.proofConfirmed(escalated)

        assertEquals(SnatchPolicy.Phase.IDLE, cleared.phase)
        assertEquals(null, cleared.eventId)
        assertEquals(0, cleared.ticks)
        assertFalse(cleared.active)
    }

    @Test
    fun `a safe-zone pause keeps the incident and moves only the next send`() {
        val escalated = SnatchPolicy.escalate(SnatchPolicy.begin("event-1", start, locked = true), start + 600_000L, 300_000L)
        val paused = SnatchPolicy.postpone(escalated, start + 700_000L, 600_000L)

        assertEquals(SnatchPolicy.Phase.ESCALATING, paused.phase)
        assertEquals(escalated.startedAtMs, paused.startedAtMs)
        assertEquals(escalated.ticks, paused.ticks)
        assertEquals(start + 700_000L + 600_000L, paused.nextDueAtMs)
        assertTrue(paused.active)
    }

    @Test
    fun `the ladder stops renewing after six hours`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true)

        assertFalse(SnatchPolicy.expired(incident, start + SnatchPolicy.INCIDENT_CAP_MS - 1L))
        assertTrue(SnatchPolicy.expired(incident, start + SnatchPolicy.INCIDENT_CAP_MS))
    }
}
