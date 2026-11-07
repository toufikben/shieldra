package com.shieldra.protection

import java.time.Duration
import java.time.Instant

/**
 * Decides, for one wrong-unlock report, whether SHIELDRA should capture and alert yet (A-33).
 *
 * The owner sets a threshold (1..5, default 2). Nothing happens for the attempts below it; once it
 * is reached, that attempt and **every** later attempt in the same session produce a capture, so a
 * thief who keeps guessing is photographed each time. A correct unlock, or a long gap, ends the
 * session and resets the count — an owner who mistypes twice in the morning and once at night is
 * never three failures deep.
 *
 * This is pure: it holds the count and the session window and returns a verdict. It touches no
 * Android API, so the threshold rule is unit-tested without a device.
 */
class FailedUnlockCounter(
    private val threshold: () -> Int,
    private val sessionGap: Duration = DEFAULT_SESSION_GAP,
) {
    private var failures = 0
    private var lastFailureAt: Instant? = null

    sealed interface Verdict {
        /** Below the threshold: recorded, nothing sent. [count] is how many in this session so far. */
        data class BelowThreshold(val count: Int) : Verdict

        /** At or past the threshold: capture and alert now. [count] is this session's attempt number. */
        data class Capture(val count: Int) : Verdict
    }

    @Synchronized
    fun onFailure(at: Instant): Verdict {
        val previous = lastFailureAt
        if (previous != null && Duration.between(previous, at) > sessionGap) {
            failures = 0
        }
        lastFailureAt = at
        failures += 1
        val limit = threshold().coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        return if (failures >= limit) Verdict.Capture(failures) else Verdict.BelowThreshold(failures)
    }

    @Synchronized
    fun onSuccess() {
        failures = 0
        lastFailureAt = null
    }

    @Synchronized
    fun sessionCount(): Int = failures

    companion object {
        const val MIN_THRESHOLD = 1
        const val MAX_THRESHOLD = 5
        const val DEFAULT_THRESHOLD = 2

        /** A fresh unlock session after this much quiet: two morning typos are not a night attempt. */
        val DEFAULT_SESSION_GAP: Duration = Duration.ofMinutes(10)
    }
}
