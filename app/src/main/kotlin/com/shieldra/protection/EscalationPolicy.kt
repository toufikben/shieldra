package com.shieldra.protection

/**
 * Decides when a burst of confirmed intrusions should escalate to the loud siren (A-33): a thief
 * who keeps trying — several wrong unlocks, a SIM swap, more movement — within a short window is
 * past the quiet-evidence stage, so the phone stops being discreet and starts screaming. One
 * intrusion never escalates; the owner sets how many within the window do.
 *
 * Pure: the intrusion times, now, and the owner's threshold in; a yes/no out. Unit-tested.
 */
object EscalationPolicy {

    const val DEFAULT_THRESHOLD = 3
    const val MIN_THRESHOLD = 2
    const val MAX_THRESHOLD = 10

    /** Intrusions older than this no longer count toward a burst. */
    const val WINDOW_MS = 10 * 60_000L

    fun shouldEscalate(
        intrusionTimesMs: List<Long>,
        nowMs: Long,
        threshold: Int,
        windowMs: Long = WINDOW_MS,
    ): Boolean {
        val t = threshold.coerceAtLeast(MIN_THRESHOLD)
        val recent = intrusionTimesMs.count { nowMs - it in 0..windowMs }
        return recent >= t
    }
}
