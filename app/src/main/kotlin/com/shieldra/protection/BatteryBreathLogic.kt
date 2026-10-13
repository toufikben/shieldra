package com.shieldra.protection

/**
 * Decides when to send a "last breath" alert as the battery runs down (A-33): a last location and
 * photo before a thief's phone dies. The owner sets the main level (default 10 %); a fixed critical
 * level (3 %) catches a fast drain that skipped the first. Each level fires once per discharge, and
 * the set resets when the phone is charging or climbs back above the main level — so a phone hovering
 * at 10 % does not alert on every tick.
 *
 * Pure: level and charging state in, verdict out. Unit-tested without a device.
 */
class BatteryBreathLogic(
    private val mainThreshold: () -> Int,
) {
    private val fired = mutableSetOf<Int>()

    sealed interface Verdict {
        data object Nothing : Verdict

        /** Crossed a level downward for the first time this discharge; [level] is which one. */
        data class Breath(val level: Int) : Verdict
    }

    @Synchronized
    fun onLevel(percent: Int, charging: Boolean): Verdict {
        val main = mainThreshold().coerceIn(MIN_LEVEL, MAX_LEVEL)
        if (charging || percent > main + RESET_HYSTERESIS) {
            fired.clear()
            return Verdict.Nothing
        }
        // Levels at or above the current reading that have not fired this discharge.
        val crossed = listOf(CRITICAL_LEVEL, main).filter { percent <= it && it !in fired }
        if (crossed.isEmpty()) return Verdict.Nothing
        // Mark every crossed level fired, but send only the most urgent (lowest), so a drop straight
        // to 2 % sends one critical alert rather than a 10 % alert on the next tick as well.
        fired.addAll(crossed)
        return Verdict.Breath(crossed.min())
    }

    companion object {
        const val MIN_LEVEL = 3
        const val MAX_LEVEL = 50
        const val DEFAULT_LEVEL = 10
        const val CRITICAL_LEVEL = 3

        /** Above main + this, the discharge is "recovered" and levels may fire again. */
        const val RESET_HYSTERESIS = 5
    }
}
