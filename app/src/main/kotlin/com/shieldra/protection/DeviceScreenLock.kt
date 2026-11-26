package com.shieldra.protection

import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * The one real screen lock in the app (A-46, owner decision 2026-11-10).
 *
 * Design B removed every lock power, and A-46 brings back exactly one: after a confirmed snatch the
 * owner wants the phone to close its screen immediately, before anything is photographed — a thief who
 * is running with the phone does not need a picture of the sky. The device-admin declaration therefore
 * gains `<force-lock />` beside `<watch-login />`, and this object is the only place that may call it.
 * The app still cannot reset the password, wipe the phone or read what was typed.
 *
 * An administrator granted before this policy existed does **not** silently gain it: the platform keeps
 * the policies it was granted with. The owner has to switch the role off and on once, which is why the
 * answer distinguishes "no admin at all" from "an admin without the lock power" — the screen can tell
 * the owner exactly which of the two they are looking at.
 */
object DeviceScreenLock {
    private const val TAG = "DeviceScreenLock"

    enum class Outcome {
        /** The screen is locked now. */
        LOCKED,

        /** No active administrator, so the app holds no lock power at all. */
        ADMIN_OFF,

        /** An administrator is active, but it was granted before force-lock: switch it off and on once. */
        NEEDS_REGRANT,

        /** The platform refused the call for another reason; the ladder retries at its next tick. */
        FAILED,
    }

    /** Whether the lock power the snatch needs is really held right now. */
    fun isGranted(context: Context): Boolean = runCatching {
        val app = context.applicationContext
        val manager = app.getSystemService(DevicePolicyManager::class.java) ?: return@runCatching false
        val admin = LockGuardAdmin.component(app)
        manager.isAdminActive(admin) &&
            manager.hasGrantedPolicy(admin, DeviceAdminInfo.USES_POLICY_FORCE_LOCK)
    }.getOrDefault(false)

    fun now(context: Context): Outcome {
        val app = context.applicationContext
        val manager = app.getSystemService(DevicePolicyManager::class.java) ?: return Outcome.ADMIN_OFF
        val admin = LockGuardAdmin.component(app)
        if (!manager.isAdminActive(admin)) return Outcome.ADMIN_OFF
        if (!manager.hasGrantedPolicy(admin, DeviceAdminInfo.USES_POLICY_FORCE_LOCK)) {
            return Outcome.NEEDS_REGRANT
        }
        return runCatching {
            manager.lockNow()
            Outcome.LOCKED
        }.getOrElse { error ->
            Log.e(TAG, "the confirmed snatch could not lock the screen: ${error.message}", error)
            Outcome.FAILED
        }
    }
}
