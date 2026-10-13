package com.shieldra.protection

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.shieldra.domain.SecurityEventType

/**
 * The owner's periodic signal (A-33): a "dead man's switch" that keeps sending until the owner
 * stops it. Each cycle ends in a capture-and-alert, then the next cycle is armed at once, so it
 * never stops on its own. A snooze notification appears before a cycle fires, letting the owner
 * postpone one cycle; whoever else holds the phone sees nothing but that neutral notification.
 *
 * The deadline is [CountdownDeadline]'s two-clock value, so moving the system clock cannot bring the
 * signal forward or push it back. Exact alarms ([AlarmManager]) wake the CPU for the firing.
 */
class CountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        when (intent?.action) {
            ACTION_EXPIRED -> {
                ProtectionService.requestCapture(appContext, SecurityEventType.COUNTDOWN_SIGNAL)
                // Continuous: arm the next cycle immediately so it never stops without the owner.
                CountdownScheduler.restart(appContext)
            }
            ACTION_WARNING -> CountdownScheduler.postSnoozeNotice(appContext)
            ACTION_SNOOZE -> {
                appContext.getSystemService(NotificationManager::class.java)?.cancel(CountdownScheduler.WARNING_NOTIFICATION_ID)
                CountdownScheduler.restart(appContext)
            }
        }
    }

    companion object {
        const val ACTION_EXPIRED = "com.shieldra.protection.COUNTDOWN_EXPIRED"
        const val ACTION_WARNING = "com.shieldra.protection.COUNTDOWN_WARNING"
        const val ACTION_SNOOZE = "com.shieldra.protection.COUNTDOWN_SNOOZE"
    }
}

/** Arms, re-arms, cancels and reschedules the countdown cycles. */
object CountdownScheduler {
    const val WARNING_NOTIFICATION_ID = 20_010
    private const val WARNING_REQUEST = 20_011
    private const val EXPIRED_REQUEST = 20_012
    private const val SNOOZE_REQUEST = 20_013
    private const val WARNING_LEAD_MS = 10 * 60_000L

    /** Starts a fresh cycle from the owner's interval and schedules its alarms. */
    fun start(context: Context) {
        val store = ProtectionSettingsStore(context)
        val settings = store.load()
        if (!settings.countdownEnabled) return
        val deadline = CountdownDeadline.start(
            durationMs = settings.countdownIntervalMs,
            nowEpochMs = System.currentTimeMillis(),
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )
        store.setCountdownDeadline(deadline)
        schedule(context, deadline)
    }

    /** Re-arms the next cycle after one fired or was snoozed; identical to [start] by design. */
    fun restart(context: Context) = start(context)

    /** Re-arms from the stored deadline after a reboot, without resetting the timer. */
    fun rescheduleAfterBoot(context: Context) {
        val store = ProtectionSettingsStore(context)
        if (!store.load().countdownEnabled) return
        val deadline = store.countdownDeadline()
        if (deadline == null) {
            start(context)
            return
        }
        // Past due after a long power-off: fire one now, which arms the next cycle.
        if (deadline.isExpired(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            ProtectionService.requestCapture(context, SecurityEventType.COUNTDOWN_SIGNAL)
            start(context)
        } else {
            schedule(context, deadline)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pending(context, CountdownReceiver.ACTION_EXPIRED, EXPIRED_REQUEST))
        alarmManager.cancel(pending(context, CountdownReceiver.ACTION_WARNING, WARNING_REQUEST))
        context.getSystemService(NotificationManager::class.java)?.cancel(WARNING_NOTIFICATION_ID)
        ProtectionSettingsStore(context).setCountdownDeadline(null)
    }

    private fun schedule(context: Context, deadline: CountdownDeadline) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val fireAt = deadline.epochDeadlineMs.coerceAtLeast(now + 1_000L)
        setExact(alarmManager, fireAt, pending(context, CountdownReceiver.ACTION_EXPIRED, EXPIRED_REQUEST))
        val warnAt = deadline.epochDeadlineMs - WARNING_LEAD_MS
        if (warnAt > now + 30_000L) {
            setExact(alarmManager, warnAt, pending(context, CountdownReceiver.ACTION_WARNING, WARNING_REQUEST))
        }
    }

    private fun setExact(alarmManager: AlarmManager, triggerAtMs: Long, operation: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            }
        } catch (error: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
        }
    }

    fun postSnoozeNotice(context: Context) {
        if (!ProtectionSettingsStore(context).load().countdownEnabled) return
        val channelId = "shieldra_countdown"
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(channelId, context.getString(com.shieldra.app.R.string.countdown_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val snooze = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST,
            Intent(context, CountdownReceiver::class.java).setAction(CountdownReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(com.shieldra.app.R.string.countdown_warning_title))
            .setContentText(context.getString(com.shieldra.app.R.string.countdown_warning_text))
            .addAction(0, context.getString(com.shieldra.app.R.string.countdown_snooze), snooze)
            .setAutoCancel(true)
            .build()
        manager.notify(WARNING_NOTIFICATION_ID, notice)
    }

    private fun pending(context: Context, action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, CountdownReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
