package com.shieldra.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * Whether the owner has given SHIELDRA the one device-admin power the Lock guard needs.
 *
 * The app declares `<watch-login />` and nothing else: it can be told that an unlock attempt failed
 * and it cannot lock the screen, reset the password, wipe the phone, or read what was typed. A
 * denial is an ordinary state, not an error — the guard simply stays off and the rest of protection
 * keeps working.
 */
object LockGuardAdmin {
    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, LockGuardReceiver::class.java)

    fun isGranted(context: Context): Boolean = runCatching {
        val manager = context.applicationContext.getSystemService(DevicePolicyManager::class.java)
        manager?.isAdminActive(component(context)) == true
    }.getOrDefault(false)
}
