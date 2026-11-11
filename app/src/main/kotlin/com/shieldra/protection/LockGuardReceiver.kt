package com.shieldra.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.shieldra.domain.SecurityEventType
import java.time.Instant

/**
 * The device-admin receiver behind the Lock guard (amendment A-33). It hears wrong unlock attempts
 * through the platform (no password is ever read, only that one failed) and, once the owner's
 * threshold is reached, asks the protection service to capture — then captures every further
 * attempt in the session too. The signal itself is rationed by [LockAlertPolicy]: the threshold
 * attempt announces, later attempts in the same incident gather evidence silently, and a new
 * incident announces again. Trying to remove device-admin rights is itself treated as a tamper and
 * triggers one capture before the right is given up.
 *
 * Android delivers [onPasswordFailed] only to an active device administrator whose XML declares
 * `<watch-login />` (AOSP `DevicePolicyManagerService.reportFailedPasswordAttempt` broadcasts
 * `ACTION_PASSWORD_FAILED`). The three-argument callback added in API 26 delegates to this
 * two-argument override, so one implementation serves every supported release.
 */
class LockGuardReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        val settings = ProtectionSettingsStore(context).load()
        if (!settings.armed || !settings.lockEnabled) return
        // A-47 D7: count first. Only the attempt that reaches the owner's threshold may accelerate an
        // open snatch; earlier attempts do nothing extra, and later ones stay on the ordinary evidence
        // path so a thief cannot turn the ladder into a stream of one alert per PIN.
        when (val verdict = LockGuardState.counter(settings.lockThreshold).onFailure(Instant.now())) {
            is FailedUnlockCounter.Verdict.Capture -> {
                if (verdict.count == settings.lockThreshold &&
                    SnatchCoordinator.escalateNow(context, verdict.count, settings.lockThreshold)
                ) {
                    return
                }
                ProtectionService.requestCapture(context, SecurityEventType.LOCK_CONFIRMED_EVENT, verdict.count)
            }
            is FailedUnlockCounter.Verdict.BelowThreshold -> Unit
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        LockGuardState.reset()
        // A-47 D6: the owner's credential at the lock screen is proof even if the runtime unlock
        // receiver missed its broadcast. When no snatch is open this is a no-op.
        SnatchCoordinator.confirmOwnerIfUnlocked(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Someone is trying to strip protection: capture once before the right is surrendered.
        val settings = ProtectionSettingsStore(context).load()
        if (settings.armed) {
            ProtectionService.requestCapture(context, SecurityEventType.LOCK_CONFIRMED_EVENT)
        }
        // A neutral, non-alarming line — nothing that tells whoever holds the phone they were seen.
        return context.getString(com.shieldra.app.R.string.device_admin_disable_warning)
    }
}

/** Process-wide unlock counter, so attempts seconds apart share one session across broadcasts. */
object LockGuardState {
    @Volatile
    private var threshold = FailedUnlockCounter.DEFAULT_THRESHOLD
    private val counter = FailedUnlockCounter(threshold = { threshold })

    fun counter(currentThreshold: Int): FailedUnlockCounter {
        threshold = currentThreshold
        return counter
    }

    fun reset() = counter.onSuccess()
}
