package com.shieldra.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * The safe-zone clock, kept outside the service process.
 *
 * The old sampler was a `Handler` inside [ProtectionService]: it died with the process, drifted in Doze,
 * and its "was inside" flag lived in a field, so every restart silently assumed the phone was at home
 * again. The one-shot leave-a-zone alert (A-36) is a crossing, and a crossing needs the last reading to
 * survive a restart — otherwise every restart would re-announce the same zone. So the tick is an alarm
 * and the state is on disk, in [ZoneStateStore].
 *
 * Exact alarms are used when the owner allowed them and idle-capable inexact ones when not — the same
 * reading the countdown and the guard patrol use — because a late zone reading is worse protection than
 * no reading, which is why [staleAfterMs] exists and why a stale reading is refused rather than trusted.
 */
object GeofenceTicker {

    const val ACTION_TICK = "com.shieldra.protection.ZONE_TICK"
    internal const val TAG = "GeofenceTicker"

    /** Distinct from the countdown, the pending capture (20020) and the guard patrol (20021). */
    private const val REQUEST_CODE = 4102

    /** A sample older than this many intervals is no longer trusted as evidence of where the phone is. */
    const val STALE_FACTOR: Long = 2L

    fun nextDelayMs(intervalMinutes: Int): Long = intervalMinutes.coerceAtLeast(1) * 60_000L

    /** How long a reading stays believable. */
    fun staleAfterMs(intervalMinutes: Int): Long = nextDelayMs(intervalMinutes) * STALE_FACTOR

    /**
     * Arms the next reading, and clears any alarm left over when the zone guard is not running. A phone
     * the owner disarmed or left without zones has no business waking to ask where it is.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val app = context.applicationContext
        val store = ProtectionSettingsStore(app)
        val settings = store.load()
        if (!settings.armed || !settings.geofenceEnabled || store.safeZones().isEmpty()) {
            cancel(app)
            return
        }
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + nextDelayMs(intervalMinutes)
        val operation = pending(app)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (error: SecurityException) {
            val inexact = runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation) }
            if (inexact.isFailure) {
                Log.w(TAG, "the safe-zone tick has no alarm: ${inexact.exceptionOrNull()?.message}")
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, GeofenceTickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * Runs one safe-zone reading when the alarm fires, then re-arms the next one. The re-arm happens first
 * for the reason the guard patrol learned: a failure in the reading or the alert must not end the
 * sampling, or the zone state silently goes stale and the ladder stops trusting it.
 *
 * Not exported: it only receives the alarm SHIELDRA itself scheduled.
 */
class GeofenceTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != GeofenceTicker.ACTION_TICK) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val store = ProtectionSettingsStore(app)
                val settings = store.load()
                runCatching { GeofenceTicker.schedule(app, settings.geofenceIntervalMinutes) }
                    .onFailure { Log.w(GeofenceTicker.TAG, "the next safe-zone tick could not be armed: ${it.message}") }
                if (!settings.armed || !settings.geofenceEnabled) {
                    Log.i(GeofenceTicker.TAG, "the safe-zone tick fired while the zone guard is off: nothing sampled")
                    return@Thread
                }
                runCatching { runBlocking { ProtectionService.sampleSafeZoneNow(app) } }
                    .onFailure { Log.w(GeofenceTicker.TAG, "the safe-zone reading failed: ${it.message}") }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
