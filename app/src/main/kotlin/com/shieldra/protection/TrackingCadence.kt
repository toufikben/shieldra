package com.shieldra.protection

/**
 * Battery-aware spacing for the post-intrusion location trail (A-34). The owner picks a base
 * interval; as the (presumably stolen) phone's battery runs down this widens the gap between pings
 * so the trail keeps reporting for longer instead of draining the last charge in a burst. While the
 * phone is charging, or its level is unknown, the base interval is used unchanged.
 *
 * Pure: base minutes, battery level and charging state in, delay out. Unit-tested without a device.
 */
object TrackingCadence {

    /** The next ping delay in milliseconds for the given base interval and battery state. */
    fun intervalMs(baseMinutes: Int, batteryPercent: Int?, charging: Boolean): Long {
        val base = baseMinutes.coerceAtLeast(1)
        val factor = when {
            charging -> 1
            batteryPercent == null -> 1
            batteryPercent <= CRITICAL_PERCENT -> CRITICAL_FACTOR
            batteryPercent <= LOW_PERCENT -> LOW_FACTOR
            else -> 1
        }
        return base.toLong() * factor * 60_000L
    }

    /** At or below this level the gap doubles. */
    const val LOW_PERCENT = 30

    /** At or below this level the gap quadruples. */
    const val CRITICAL_PERCENT = 15

    const val LOW_FACTOR = 2
    const val CRITICAL_FACTOR = 4

    /**
     * The range the owner's own cadence may take. Below [MIN_INTERVAL_MINUTES] the trail is a stream
     * that drains the battery it is trying to outlast; above [MAX_INTERVAL_MINUTES] the trail is too
     * sparse to follow a moving phone. A run shorter than [MIN_DURATION_MINUTES] ends before the first
     * gap has widened; longer than [MAX_DURATION_MINUTES] it keeps reporting long after the thief has
     * stopped moving. Published here so the settings screen offers exactly the range the trail honours.
     */
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 30
    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = 240
}
