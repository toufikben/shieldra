package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The snatch ladder is decided without a device: ten minutes of proof, then full evidence at the
 * owner's cadence, a wrong PIN only as an accelerator, and a phone that slows down before its last
 * percent is spent. The battery case is the owner's 10% agreement, so it is stated here rather than
 * left to a comment in the coordinator.
 *
 * A-47 adds the two halves that make the challenge liveable: the unlock is proof the moment it
 * happens (early checks across process death, a persisted proof moment), and a wrong PIN accelerates
 * only at the owner's own lock threshold.
 */
class SnatchPolicyTest {

    private val start = 1_000_000L

    @Test
    fun `a confirmed snatch begins by waiting for proof, not by capturing`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)

        assertEquals(SnatchPolicy.Phase.AWAITING_PROOF, incident.phase)
        assertEquals("event-1", incident.eventId)
        assertEquals(start + SnatchPolicy.PROOF_WINDOW_MS, incident.nextDueAtMs)
        assertEquals(start, incident.lockedAtMs)
        assertEquals(0, incident.ticks)
        assertEquals(0L, incident.proofSeenAtMs)
        assertTrue(incident.secureAtBegin)
        assertTrue(incident.active)
    }

    @Test
    fun `an incident whose lock was refused is kept, because a granted admin can heal it at a check`() {
        // A-47 D8 refuses to *open* a challenge when the screen cannot be locked. The durable incident
        // still models that state: an incident stored before the refusal, or a legacy A-46 one, keeps
        // lockedAtMs zero until a check can lock, so nothing is escalated from a demand nobody can clear.
        val incident = SnatchPolicy.begin("event-1", start, locked = false, secureAtBegin = true)

        assertEquals(0L, incident.lockedAtMs)
        assertFalse(SnatchPolicy.canProveByUnlock(incident))
        assertTrue(incident.active)
    }

    @Test
    fun `no proof after the window escalates on its own`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)
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
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)

        assertFalse(SnatchPolicy.unlockedSince(incident, start + 14_000L, keyguardLocked = false))
        assertTrue(SnatchPolicy.unlockedSince(incident, start + 16_000L, keyguardLocked = false))
        assertFalse(SnatchPolicy.unlockedSince(incident, start + 60_000L, keyguardLocked = true))
    }

    @Test
    fun `a phone that was never locked cannot prove ownership by being unlocked`() {
        val unlockedAtBegin = SnatchPolicy.begin("event-1", start, locked = false, secureAtBegin = true)
        assertFalse(SnatchPolicy.unlockedSince(unlockedAtBegin, start + 60_000L, keyguardLocked = false))
        assertFalse(SnatchPolicy.canProveByUnlock(unlockedAtBegin))
    }

    @Test
    fun `a phone without a secure lock is never challenged as provable`() {
        val noCredential = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = false)

        assertFalse(SnatchPolicy.canProveByUnlock(noCredential))
        // The unlock rule itself still needs a lock; D8 keeps such a phone out of the challenge in the
        // coordinator, and this predicate is the second line of defence.
        assertFalse(SnatchPolicy.canProveByUnlock(SnatchPolicy.Incident()))
    }

    @Test
    fun `a secure locked incident is provable by unlock`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)
        assertTrue(SnatchPolicy.canProveByUnlock(incident))
    }

    @Test
    fun `proof records its moment and stops the early checks`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)
        assertEquals(start + 20_000L, SnatchPolicy.nextProofCheckMs(incident, start))

        val proven = SnatchPolicy.proofSeen(incident, start + 17_000L)

        assertEquals(start + 17_000L, proven.proofSeenAtMs)
        assertEquals(incident.phase, proven.phase)
        assertNull(SnatchPolicy.nextProofCheckMs(proven, start + 17_000L))
    }

    @Test
    fun `the early checks run at twenty seconds, one minute, three minutes and five minutes`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)

        assertEquals(start + 20_000L, SnatchPolicy.nextProofCheckMs(incident, start))
        assertEquals(start + 60_000L, SnatchPolicy.nextProofCheckMs(incident, start + 20_000L))
        assertEquals(start + 180_000L, SnatchPolicy.nextProofCheckMs(incident, start + 60_000L))
        assertEquals(start + 300_000L, SnatchPolicy.nextProofCheckMs(incident, start + 180_000L))
        // The checks are spent before the window ends; the tenth-minute ladder tick is the ceiling.
        assertNull(SnatchPolicy.nextProofCheckMs(incident, start + 300_000L))
    }

    @Test
    fun `the checks are anchored to the lock, not to the confirmation`() {
        val lateLock = SnatchPolicy.begin("event-1", start, locked = false, secureAtBegin = true)
            .copy(lockedAtMs = start + 7_000L)

        // Five seconds in, the first check is still ahead; it is the lock's twentieth-second.
        assertEquals(start + 27_000L, SnatchPolicy.nextProofCheckMs(lateLock, start + 5_000L))
        // A checkpoint that would land past the proof window is never scheduled: the ladder tick is
        // already the ceiling there, and a check after it would only repeat the same reading.
        assertNull(
            SnatchPolicy.nextProofCheckMs(
                lateLock.copy(lockedAtMs = start + 400_000L),
                start + 585_000L,
            ),
        )
    }

    @Test
    fun `a wrong PIN accelerates only at the owner's own threshold`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)

        assertFalse(SnatchPolicy.acceleratesOnFailedUnlock(incident, 1, 3))
        assertFalse(SnatchPolicy.acceleratesOnFailedUnlock(incident, 2, 3))
        assertTrue(SnatchPolicy.acceleratesOnFailedUnlock(incident, 3, 3))
        // Later attempts keep the ordinary lock-evidence path; they must not stream the ladder.
        assertFalse(SnatchPolicy.acceleratesOnFailedUnlock(incident, 4, 3))
        // Misconfiguration is not an accelerator.
        assertFalse(SnatchPolicy.acceleratesOnFailedUnlock(incident, 0, 0))
        // A closed incident is never accelerated.
        assertFalse(SnatchPolicy.acceleratesOnFailedUnlock(SnatchPolicy.Incident(), 3, 3))
    }

    @Test
    fun `proof clears the whole incident`() {
        val escalated = SnatchPolicy.escalate(
            SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true),
            start + 600_000L,
            300_000L,
        )
        val cleared = SnatchPolicy.proofConfirmed(escalated)

        assertEquals(SnatchPolicy.Phase.IDLE, cleared.phase)
        assertEquals(null, cleared.eventId)
        assertEquals(0, cleared.ticks)
        assertFalse(cleared.active)
    }

    @Test
    fun `a safe-zone pause keeps the incident and moves only the next send`() {
        val escalated = SnatchPolicy.escalate(
            SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true),
            start + 600_000L,
            300_000L,
        )
        val paused = SnatchPolicy.postpone(escalated, start + 700_000L, 600_000L)

        assertEquals(SnatchPolicy.Phase.ESCALATING, paused.phase)
        assertEquals(escalated.startedAtMs, paused.startedAtMs)
        assertEquals(escalated.ticks, paused.ticks)
        assertEquals(start + 700_000L + 600_000L, paused.nextDueAtMs)
        assertTrue(paused.active)
    }

    @Test
    fun `the ladder stops renewing after six hours`() {
        val incident = SnatchPolicy.begin("event-1", start, locked = true, secureAtBegin = true)

        assertFalse(SnatchPolicy.expired(incident, start + SnatchPolicy.INCIDENT_CAP_MS - 1L))
        assertTrue(SnatchPolicy.expired(incident, start + SnatchPolicy.INCIDENT_CAP_MS))
    }
}
