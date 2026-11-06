package com.shieldra.protection

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The "Alarms and reminders" special access the countdown and the watchdog schedule against (A-33).
 * Android 14+ denies SCHEDULE_EXACT_ALARM by default, and Google Play reserves USE_EXACT_ALARM for
 * alarm-clock and calendar apps, so SHIELDRA never declares that second permission. Without this
 * grant the guards still fire — each scheduling call site checks the same platform grant and falls
 * back to an inexact alarm — the owner just loses second-level timing. The platform API stays in
 * this protection package; the UI only reads the state and opens the prompt.
 */
object ExactAlarmAccess {
    fun isGranted(context: Context): Boolean {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        // Before Android 12 there was no exact-alarm grant: every app could schedule exactly, so the
        // access counts as granted and the checklist row needs no action (the same reading the three
        // scheduling call sites use when they fall back).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** Opens the system's exact-alarm grant for this app; falls back to the app's detail page. */
    fun request(context: Context) {
        // Nothing to grant before Android 12, and the settings action does not exist there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val direct = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isFailure) {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(details) }
        }
    }
}
