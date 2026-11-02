package com.shieldra.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime

/**
 * The guard heartbeat patrol (audit development أ): one alarm, unrelated to the protection service,
 * that asks every half hour whether the service is still stamping checks — and tells the owner when it
 * is not.
 *
 * Why the service cannot ask this about itself: the failure being measured is the service dying. On the
 * owner's skin a foreground service is killed by the battery manager days before anything is stolen,
 * and nothing on the phone says so. An alarm the service re-armed on every heartbeat would stop with
 * it, so the question is asked from outside: [GuardWatchdog] arms one exact alarm, this receiver wakes,
 * reads the stamp the service last wrote, and reports the gap through the owner's own channels.
 *
 * It re-arms the next window *before* it does anything else, so a failure in the reporting path cannot
 * end the patrol. A silence is reported once per spell, never every window, because an alert that
 * cries wolf on ordinary battery throttling is an alert that gets silenced.
 *
 * Not exported: it only receives the alarm SHIELDRA itself scheduled.
 */
class GuardWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        if (intent?.action != ACTION_CHECK) return
        runCatching { GuardWatchdog.schedule(app) }
            .onFailure { Log.e(GuardWatchdog.TAG, "the next guard-silence check could not be armed: ${it.message}", it) }

        val store = ProtectionSettingsStore(app)
        if (!store.load().armed) {
            // Disarmed between the alarm and this firing: the owner turned the guards off, so their
            // silence is their own choice and there is nothing to report.
            Log.i(GuardWatchdog.TAG, "protection is disarmed: no silence to report")
            return
        }
        val now = System.currentTimeMillis()
        when (
            val verdict = GuardWatchdogLogic.decide(
                lastCheckEpochMs = store.lastCheckEpochMs(),
                lastReportedEpochMs = store.guardSilenceReportedAt(),
                nowEpochMs = now,
            )
        ) {
            GuardWatchdogLogic.Verdict.Watching ->
                Log.d(GuardWatchdog.TAG, "the guards stamped a check inside the window")
            GuardWatchdogLogic.Verdict.AlreadyReported ->
                Log.i(GuardWatchdog.TAG, "this silence was already reported; waiting for a stamp to end it")
            is GuardWatchdogLogic.Verdict.Silent -> {
                Log.e(
                    GuardWatchdog.TAG,
                    "no guard check has been stamped for ${verdict.minutesSilent} min while armed" +
                        if (verdict.everRan) "" else " — the service never started",
                )
                report(app, store, verdict, now)
            }
        }
    }

    /**
     * Sends the gap through the owner's configured channels and marks this spell reported. The stamp is
     * written after the send was handed over: a dispatch that threw keeps the silence unreported, so the
     * next window asks again rather than the app believing it had warned someone it never warned.
     *
     * A guard that never stamped a check is sent without a duration. "Silent for 30 million minutes"
     * would be arithmetically true and useless: the number is measured from the last stamp, and there
     * was none, so the honest message is the title alone.
     */
    private fun report(
        app: Context,
        store: ProtectionSettingsStore,
        verdict: GuardWatchdogLogic.Verdict.Silent,
        now: Long,
    ) {
        runCatching {
            AlertRuntime.alert(
                app,
                AlertRuntime.snapshot(app, AlertKind.GUARD_SILENT, eventId = null)
                    .copy(silentMinutes = if (verdict.everRan) verdict.minutesSilent else null),
            )
        }.onSuccess {
            store.setGuardSilenceReportedAt(now)
        }.onFailure {
            Log.e(GuardWatchdog.TAG, "the guard silence could not be reported to the owner: ${it.message}", it)
        }
    }
}

/** Arms, re-arms and cancels the patrol alarm. One window, one alarm, no stored deadline. */
object GuardWatchdog {
    const val ACTION_CHECK = "com.shieldra.protection.GUARD_SILENCE_CHECK"
    internal const val TAG = "GuardWatchdog"
    private const val REQUEST_CODE = 20_021

    /**
     * Arms the next check. Refuses, and clears any alarm left over, while protection is disarmed: a
     * patrol that keeps waking a phone the owner turned off would be a bug wearing the costume of a
     * feature — and the alarm is what the owner's battery-bill reputation is built on.
     */
    fun schedule(context: Context) {
        val app = context.applicationContext
        if (!ProtectionSettingsStore(app).load().armed) {
            cancel(app)
            return
        }
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = GuardWatchdogLogic.nextFireAt(System.currentTimeMillis())
        val operation = pending(app)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (error: SecurityException) {
            // No permission for exact alarms: an idle-capable inexact alarm still asks the question, later.
            val inexact = runCatching {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
            if (inexact.isFailure) {
                Log.w(TAG, "the guard patrol has no alarm: ${inexact.exceptionOrNull()?.message}")
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, GuardWatchdogReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
