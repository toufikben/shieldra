package com.shieldra.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Runs the captures that were queued when the service could not start (A-33 capture-path fix).
 *
 * The queue alone would not be enough. When a background trigger is denied its foreground-service
 * start, nothing is running that could drain it, so the request would wait until the owner happens to
 * open the app — which is the very silence this exists to end. The failure therefore arms one short
 * alarm, and this receiver wakes the app to try again.
 *
 * It limits itself: a request older than [PendingCaptureLogic.STALE_AFTER_MS] is no longer run, and a
 * queue that is empty cancels the alarm, so retries stop within a few minutes on their own.
 *
 * Not exported: it only receives the alarm SHIELDRA itself scheduled.
 */
class PendingCaptureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DRAIN) return
        ProtectionService.drainPendingCaptures(context.applicationContext)
    }

    companion object {
        const val ACTION_DRAIN = "com.shieldra.protection.DRAIN_PENDING_CAPTURES"
        private const val TAG = "PendingCaptureReceiver"
        private const val REQUEST_CODE = 20_020

        /** Long enough for a busy camera or a closing database to free itself, short enough to catch the same scene. */
        private const val RETRY_DELAY_MS = 30_000L

        /** Asks for one retry soon; a second call simply moves the same alarm. */
        fun schedule(context: Context, delayMs: Long = RETRY_DELAY_MS) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + delayMs
            val operation = pending(context)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                }
            } catch (error: SecurityException) {
                // No permission for exact alarms: an idle-capable inexact alarm still wakes the retry, later.
                val inexact = runCatching {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                }
                if (inexact.isFailure) {
                    Log.w(TAG, "pending captures have no retry alarm: ${inexact.exceptionOrNull()?.message}")
                }
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)
                ?.cancel(pending(context))
        }

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PendingCaptureReceiver::class.java).setAction(ACTION_DRAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
