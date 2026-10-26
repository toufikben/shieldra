package com.shieldra.app.settings

/**
 * When the app lock puts its screen back in front of Shieldra (amendment A-39). The lock is a UI gate
 * behind the platform's own biometric / screen-lock prompt: it shows on every cold start, and again
 * when the app comes back after being away longer than a short grace. It is not tied to the evidence
 * keys (those stay as EvidenceKeyPolicy says); it stops someone who picks up an unlocked phone from
 * simply opening the app.
 *
 * Pure: monotonic times in, a yes/no out. Unit-tested.
 */
object AppLockPolicy {

    /** Coming back inside this window (a permission dialog, a picker, a call) does not re-lock. */
    const val GRACE_MS = 30_000L

    fun lockOnColdStart(enabled: Boolean): Boolean = enabled

    fun shouldLockOnReturn(
        enabled: Boolean,
        backgroundedAtMs: Long?,
        nowMs: Long,
        graceMs: Long = GRACE_MS,
    ): Boolean {
        if (!enabled) return false
        val left = backgroundedAtMs ?: return false
        if (nowMs < left) return true // a clock that went backwards is not a reason to stay unlocked
        return nowMs - left >= graceMs
    }
}

/**
 * Whether the owner has already unlocked in this process. A new process (a cold start, or Android
 * restoring the app after killing it) starts locked, so a restored activity can never skip the lock;
 * an in-process recreate (a language change, a rotation) keeps it unlocked.
 */
object AppLockSession {
    @Volatile
    var unlocked: Boolean = false
}
