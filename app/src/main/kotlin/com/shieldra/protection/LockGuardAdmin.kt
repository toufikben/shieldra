package com.shieldra.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Whether the owner has given SHIELDRA the device-admin powers its two sensors need.
 *
 * The app declares `<watch-login />` and `<force-lock />` and nothing else: it can be told that an
 * unlock attempt failed, and it can close the screen after a confirmed snatch (A-46). It cannot reset
 * the password, wipe the phone, or read what was typed. A denial is an ordinary state, not an error —
 * the failed-unlock guard simply stays off, the snatch lock reports `NEEDS_REGRANT`, and the rest of
 * protection keeps working.
 */
object LockGuardAdmin {
    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, LockGuardReceiver::class.java)

    fun isGranted(context: Context): Boolean = runCatching {
        val manager = context.applicationContext.getSystemService(DevicePolicyManager::class.java)
        manager?.isAdminActive(component(context)) == true
    }.getOrDefault(false)
}
