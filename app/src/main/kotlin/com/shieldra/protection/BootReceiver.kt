package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings protection back after a reboot (amendment A-33 / A-10 relaxation). A thief who power-cycles
 * the phone to shake off tracking finds the guards running again: the Lock guard and the countdown
 * send their text alerts at once. A fresh photo and location return once the owner next opens the
 * app, because Android 14+ only restores camera/location to a foreground service the user launched
 * from the app — the honest, policy-respecting limit, with no workaround.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                if (ProtectionSettingsStore(appContext).load().armed) {
                    ProtectionService.arm(appContext)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
