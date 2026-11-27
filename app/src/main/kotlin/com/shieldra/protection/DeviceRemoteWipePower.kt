package com.shieldra.protection

import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.content.Context

/**
 * Whether the administrator Shieldra holds really carries the wipe power (P2-3).
 *
 * An administrator granted before this policy existed does **not** silently gain it: the platform keeps
 * the policies it was granted with. Asking `hasGrantedPolicy` separates "the owner must re-grant the
 * role once" from "the call failed for another reason", which is the difference between a sentence the
 * owner can act on and a mystery.
 */
object DeviceRemoteWipePower {

    /** Whether the wipe power is really held right now. */
    fun isGranted(context: Context): Boolean = runCatching {
        val app = context.applicationContext
        val manager = app.getSystemService(DevicePolicyManager::class.java) ?: return@runCatching false
        val admin = LockGuardAdmin.component(app)
        manager.isAdminActive(admin) &&
            manager.hasGrantedPolicy(admin, DeviceAdminInfo.USES_POLICY_WIPE_DATA)
    }.getOrDefault(false)
}
