package com.shieldra.protection

/** What the ladder does next after a confirmed Motion. */
enum class LadderAction { EVIDENCE_BURST, SETTLED }

/** The next step: what to run, how long from now, and which burst number it is. */
data class LadderTick(val action: LadderAction, val delayMs: Long, val burst: Int)

/**
 * The timing of the A-45 snatch ladder (design D1).
 *
 * The owner's complaint about the old behaviour was that a single shot at the moment of the snatch is
 * worth little: the frame is a blur and the thief is already running. So the ladder locks and demands
 * an unlock first, and only spends the camera, the microphone and the alert train once nobody has
 * unlocked. The fast phase exists because the first minutes are where a thief is still moving and a
 * frame can still identify him; the slow phase exists because after that the useful thing is where the
 * phone is going, and every extra capture costs battery on a phone the owner may never get back.
 *
 * Pure: elapsed time and bursts already fired in, the next delay out. Tested without a device.
 */
object SnatchLadder {

    /** How long the holder is given to unlock before the first evidence burst. */
    const val DEMAND_MS = 30_000L

    /** The gap between bursts in the fast phase. */
    const val BURST_INTERVAL_MS: Long = 60_000L

    /** How many bursts run at the fast cadence after the first one. */
    const val FAST_BURSTS = 5

    /** The gap between bursts once the fast phase is spent. */
    const val SLOW_INTERVAL_MS: Long = 300_000L

    /** Nothing is scheduled past this age of the incident: the ladder settles. */
    const val LADDER_WINDOW_MS = 1_800_000L

    /** When burst number [burst] is due, measured from the start of the incident. */
    fun burstAtMs(burst: Int): Long = when {
        burst <= 1 -> DEMAND_MS
        burst <= FAST_BURSTS + 1 -> DEMAND_MS + (burst - 1) * BURST_INTERVAL_MS
        else -> DEMAND_MS + FAST_BURSTS * BURST_INTERVAL_MS +
            (burst - FAST_BURSTS - 1) * SLOW_INTERVAL_MS
    }

    /**
     * The next step for an incident that started [elapsedMs] ago and has already run [burstsDone]
     * bursts. A burst that is already overdue collapses into "run it now"; a burst that would fall
     * outside the window never happens.
     */
    fun next(elapsedMs: Long, burstsDone: Int): LadderTick {
        val burst = burstsDone + 1
        val due = burstAtMs(burst)
        if (due >= LADDER_WINDOW_MS) return LadderTick(LadderAction.SETTLED, 0L, burstsDone)
        return LadderTick(LadderAction.EVIDENCE_BURST, (due - elapsedMs).coerceAtLeast(0L), burst)
    }
}
