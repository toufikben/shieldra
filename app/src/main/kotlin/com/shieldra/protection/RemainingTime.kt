package com.shieldra.protection

/** A time left, in the two largest honest units: "9 h 58 min", "10 min", "<1 min". Pure so it is tested. */
object RemainingTime {
    fun parts(remainingMs: Long): Triple<Long, Long, Long> {
        val totalMinutes = (remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L
        return Triple(totalMinutes / 1_440, totalMinutes % 1_440 / 60, totalMinutes % 60)
    }

    fun format(remainingMs: Long, day: String, hour: String, minute: String, lessThanMinute: String): String {
        val (d, h, m) = parts(remainingMs)
        return when {
            remainingMs <= 0L -> lessThanMinute
            d > 0 -> if (h > 0) "$d $day $h $hour" else "$d $day"
            h > 0 -> if (m > 0) "$h $hour $m $minute" else "$h $hour"
            m > 0 -> "$m $minute"
            else -> lessThanMinute
        }
    }
}
