package com.shieldra.protection

/**
 * The deadline arithmetic for the owner's periodic signal (A-33), kept pure and tamper-resistant.
 *
 * A countdown is stored as two deadlines taken when it starts: one on the monotonic clock
 * ([elapsedDeadlineMs]) and one on the wall clock ([epochDeadlineMs]). Within one boot the monotonic
 * clock is the trusted one: it counts real elapsed time including deep sleep and the owner cannot
 * move it, so [remainingMs]/[isExpired] use it alone — moving the wall clock forward or back changes
 * nothing. The wall-clock deadline exists only for the two things the monotonic clock cannot do:
 * drive an `RTC_WAKEUP` alarm, and survive a reboot (where the monotonic clock resets to zero). Boot
 * recovery reads [isEpochExpired].
 *
 * It computes; the scheduler and the service act on what it returns. No Android types here, so the
 * rule is unit-tested without a device.
 */
data class CountdownDeadline(
    val epochDeadlineMs: Long,
    val elapsedDeadlineMs: Long,
) {
    /** Milliseconds left on the monotonic clock, never negative. The owner cannot tamper with this. */
    fun remainingMs(nowElapsedMs: Long): Long = (elapsedDeadlineMs - nowElapsedMs).coerceAtLeast(0L)

    fun isExpired(nowElapsedMs: Long): Boolean = remainingMs(nowElapsedMs) == 0L

    /** Boot recovery only: the monotonic clock reset at reboot, so the wall clock decides here. */
    fun isEpochExpired(nowEpochMs: Long): Boolean = nowEpochMs >= epochDeadlineMs

    companion object {
        /** Floor so a mis-set "every 0 ms" cannot busy-loop the capture path. */
        const val MIN_DURATION_MS = 60_000L

        /** The durations the owner may pick (A-33): 1 min up to 7 days, plus a custom value. */
        val PRESETS_MS: List<Long> = listOf(
            60_000L, // 1 minute
            15 * 60_000L, // 15 minutes
            30 * 60_000L, // 30 minutes
            60 * 60_000L, // 1 hour
            2 * 60 * 60_000L, // 2 hours
            6 * 60 * 60_000L, // 6 hours
            12 * 60 * 60_000L, // 12 hours
            24 * 60 * 60_000L, // 1 day
            2 * 24 * 60 * 60_000L, // 2 days
            3 * 24 * 60 * 60_000L, // 3 days
            7 * 24 * 60 * 60_000L, // 7 days
        )

        /** Starts a cycle: both deadlines from the one duration, floored. */
        fun start(durationMs: Long, nowEpochMs: Long, nowElapsedMs: Long): CountdownDeadline {
            val safe = durationMs.coerceAtLeast(MIN_DURATION_MS)
            return CountdownDeadline(nowEpochMs + safe, nowElapsedMs + safe)
        }
    }
}
