package com.shieldra.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Locks the screen when the owner sends `/lock` from their own Telegram chat. This is the only
 * caller of `lockNow()` in the app: WATCH_LOGIN hears a failed unlock, FORCE_LOCK locks on request,
 * and SHIELDRA holds no power to wipe the phone, change its password or read it.
 *
 * A device administrator granted before `force-lock` was added keeps the policy set it was granted
 * with, so the first refused call is reported as [Result.NEEDS_REGRANT] and the reply tells the
 * owner to switch the role off and on once. A refused call is never retried silently.
 */
object DeviceScreenLock {
    enum class Result { LOCKED, ADMIN_OFF, NEEDS_REGRANT, FAILED }

    fun now(context: Context): Result {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return Result.FAILED
        val admin = ComponentName(context, LockGuardReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return Result.ADMIN_OFF
        return try {
            dpm.lockNow()
            Result.LOCKED
        } catch (e: SecurityException) {
            // The active administrator was never granted force-lock: the owner has to re-enable it.
            Result.NEEDS_REGRANT
        } catch (e: RuntimeException) {
            Result.FAILED
        }
    }
}
