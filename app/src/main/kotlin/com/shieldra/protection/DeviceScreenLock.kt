package com.shieldra.protection

import android.app.admin.DeviceAdminInfo
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

    /**
     * True only when the role is active **and** `force-lock` was really granted. An administrator
     * granted before this policy existed fails this check, so the protection and health screens
     * offer the one-time re-grant instead of showing a lock that would be refused.
     */
    fun isGranted(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = ComponentName(context, LockGuardReceiver::class.java)
        return runCatching {
            dpm.isAdminActive(admin) &&
                dpm.hasGrantedPolicy(admin, DeviceAdminInfo.USES_POLICY_FORCE_LOCK)
        }.getOrDefault(false)
    }

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
