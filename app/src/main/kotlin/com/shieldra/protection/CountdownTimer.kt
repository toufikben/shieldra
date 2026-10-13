package com.shieldra.protection

/**
 * The deadline arithmetic for the owner's periodic signal (A-33), kept pure and tamper-resistant.
 *
 * A countdown is stored as two deadlines taken when it starts: one on the wall clock
 * ([epochDeadlineMs]) and one on the monotonic clock ([elapsedDeadlineMs]). Remaining time is the
 * **smaller** of the two, so moving the system clock forward cannot make the signal fire early, and
 * moving it back cannot postpone it past the real duration. Deep sleep freezes the monotonic clock,
 * so the wall clock covers that gap; a clock jump is covered the other way. Neither alone is
 * trusted.
 *
 * It computes; the scheduler and the service act on what it returns. No Android types here, so the
 * rule is unit-tested without a device.
 */
data class CountdownDeadline(
    val epochDeadlineMs: Long,
    val elapsedDeadlineMs: Long,
) {
    /** Milliseconds left, never negative, given the current wall and monotonic readings. */
    fun remainingMs(nowEpochMs: Long, nowElapsedMs: Long): Long {
        val byEpoch = epochDeadlineMs - nowEpochMs
        val byElapsed = elapsedDeadlineMs - nowElapsedMs
        return minOf(byEpoch, byElapsed).coerceAtLeast(0L)
    }

    fun isExpired(nowEpochMs: Long, nowElapsedMs: Long): Boolean =
        remainingMs(nowEpochMs, nowElapsedMs) == 0L

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
