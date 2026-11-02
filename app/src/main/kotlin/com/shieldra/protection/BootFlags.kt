package com.shieldra.protection

import android.content.Context

/**
 * The one fact the app has to survive a restart-and-no-unlock with: whether protection was armed.
 *
 * It lives in device-protected storage for that reason and no other. Everything else — the delivery
 * coordinates, the evidence key, the recorded events — stays behind the owner's unlock credential,
 * where Android keeps it by default and where a thief who reboots the phone cannot read it either. A
 * boolean is the whole of what is worth having on the unlocked side of the lock screen.
 *
 * The mirror is written where the decision is made ([ProtectionRuntime.arm], [ProtectionRuntime.disarm],
 * a settings save) and re-synced from the real settings every time they are read, so the two can drift
 * only for an arm made before this file existed.
 */
class BootFlags(context: Context) {

    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Whether the owner had protection armed. Safe default: no. */
    fun armed(): Boolean = prefs.getBoolean(KEY_ARMED, false)

    /** Keeps the mirror equal to the stored settings, which is the only version of the truth. */
    fun sync(armed: Boolean) {
        prefs.edit().putBoolean(KEY_ARMED, armed).apply()
    }

    /**
     * Stamps the fact that the phone came back up. Returns false when nothing was armed, so the caller
     * can log the difference between a restarted guard and a restarted phone.
     */
    fun recordReboot(nowMs: Long = System.currentTimeMillis()): Boolean {
        val stamp = BootInterruptLogic.record(armed(), nowMs)
        if (stamp == BootInterruptLogic.NO_REBOOT) return false
        prefs.edit().putLong(KEY_REBOOTED_AT, stamp).apply()
        return true
    }

    /**
     * Reads the restart record and removes it, so a boot is reported once and never again — the app has
     * no way to tell a second read from a new boot, and a repeated message would be a false report.
     */
    fun takeReboot(nowMs: Long = System.currentTimeMillis()): BootInterruption {
        val rebootedAt = prefs.getLong(KEY_REBOOTED_AT, BootInterruptLogic.NO_REBOOT)
        val result = BootInterruptLogic.interruption(armed(), rebootedAt, nowMs)
        if (rebootedAt != BootInterruptLogic.NO_REBOOT) prefs.edit().remove(KEY_REBOOTED_AT).apply()
        return result
    }

    /** Forgets the restart record without reporting it, when protection has gone off in the meantime. */
    fun clearReboot() {
        prefs.edit().remove(KEY_REBOOTED_AT).apply()
    }

    private companion object {
        const val NAME = "shieldra_boot"
        const val KEY_ARMED = "armed"
        const val KEY_REBOOTED_AT = "rebooted_at"
    }
}
