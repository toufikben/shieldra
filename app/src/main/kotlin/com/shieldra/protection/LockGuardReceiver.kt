package com.shieldra.protection

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.shieldra.domain.SecurityEventType
import java.time.Instant

/**
 * The device-admin receiver behind the Lock guard (amendment A-33). It hears wrong unlock attempts
 * through the platform (no password is ever read, only that one failed) and, once the owner's
 * threshold is reached, asks the protection service to capture and alert — then on every further
 * attempt in the session. Trying to remove device-admin rights is itself treated as a tamper and
 * triggers one capture before the right is given up.
 */
class LockGuardReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        val settings = ProtectionSettingsStore(context).load()
        if (!settings.armed || !settings.lockEnabled) return
        when (val verdict = LockGuardState.counter(settings.lockThreshold).onFailure(Instant.now())) {
            is FailedUnlockCounter.Verdict.Capture ->
                ProtectionService.requestCapture(context, SecurityEventType.LOCK_CONFIRMED_EVENT, verdict.count)
            is FailedUnlockCounter.Verdict.BelowThreshold -> Unit
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        LockGuardState.reset()
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
