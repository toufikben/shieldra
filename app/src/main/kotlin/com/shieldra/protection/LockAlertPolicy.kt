package com.shieldra.protection

import com.shieldra.domain.SecurityEventType

/**
 * Decides whether one lock capture is also announced, or only recorded (A-33, owner report 2026-11-08).
 *
 * The owner's agreed signal is **one message per unlock session**: the attempt that reaches the
 * threshold announces, and every further attempt in that session still photographs and stores its
 * evidence but stays silent. Capturing each attempt is the guard; messaging after each one turned a
 * thief's guessing into a stream of signals, which is not what the owner asked for. A new incident
 * after the ten-minute grouping window announces again, and a capture without an attempt count (the
 * device-admin removal tamper) always announces. Every non-lock capture is outside this policy.
 *
 * Kept free of Android so the rule that turns a burst of wrong attempts into one signal is testable
 * on the JVM.
 */
object LockAlertPolicy {

    fun announces(
        type: SecurityEventType,
        incidentIsNew: Boolean,
        failedAttempts: Int?,
        threshold: Int,
    ): Boolean {
        if (type != SecurityEventType.LOCK_CONFIRMED_EVENT) return true
        // The tamper path (trying to strip device-admin) carries no attempt count; it is never silent.
        if (failedAttempts == null) return true
        // A fresh incident is a new story for the owner, even if it follows an older one.
        if (incidentIsNew) return true
        // Within one open incident, only the threshold attempt speaks; later ones keep gathering proof.
        val limit = threshold.coerceIn(FailedUnlockCounter.MIN_THRESHOLD, FailedUnlockCounter.MAX_THRESHOLD)
        return failedAttempts == limit
    }
}
