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
}
