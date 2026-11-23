package com.shieldra.protection

import android.content.Context
import android.provider.Settings

/**
 * Reads the two debugging switches the tamper watch judges (P2-1).
 *
 * Both are plain `Settings.Global` integers, so no permission is involved and nothing about the
 * owner's data is touched. A setting the platform will not answer reads as 0 rather than throwing:
 * a reading that could not be taken must not be reported as a door that opened.
 */
object TamperSettings {

    fun snapshot(context: Context): TamperSnapshot {
        val resolver = context.applicationContext.contentResolver
        return TamperSnapshot(
            usbDebugging = globalFlag(resolver, Settings.Global.ADB_ENABLED),
            developerOptions = globalFlag(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        )
    }

    private fun globalFlag(
        resolver: android.content.ContentResolver,
        key: String,
    ): Boolean = runCatching { Settings.Global.getInt(resolver, key, 0) != 0 }.getOrDefault(false)
}
