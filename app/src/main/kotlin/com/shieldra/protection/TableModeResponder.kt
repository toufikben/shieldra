package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime

/**
 * Runs the owner's table-mode response when the Motion guard confirms the phone was lifted (A-33).
 *
 * The owner picks one of three responses:
 *  - SILENT: capture a photo and a fix and send, quietly.
 *  - SIREN: the same, plus a loud alarm at once.
 *  - GRACE_THEN_SIREN: a few seconds' grace first (set by the owner) in which unlocking the phone
 *    cancels everything — so picking up your own phone does nothing — and only if no one unlocks does
 *    the alarm sound and the alert go out.
 *
 * The capture goes through the live protection service, so the photo and fix really attach to the
 * Motion event; if protection is not armed, the guard keeps its own text-only alert.
 */
object TableModeResponder {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var unlockReceiver: BroadcastReceiver? = null

    /** How long the siren is allowed to sound before it stops itself, if nothing else stops it. */
    private const val SIREN_MAX_MS = 30_000L

    fun onMotionConfirmed(context: Context, eventId: String) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        when (settings.motionResponse) {
            ProtectionSettings.MotionResponse.SILENT -> fire(app, eventId, siren = false)
            ProtectionSettings.MotionResponse.SIREN -> fire(app, eventId, siren = true)
            ProtectionSettings.MotionResponse.GRACE_THEN_SIREN ->
                scheduleAfterGrace(app, eventId, settings.motionGraceSeconds)
        }
    }

    @Synchronized
    private fun scheduleAfterGrace(context: Context, eventId: String, graceSeconds: Int) {
        cancel(context)
        registerUnlockCancel(context)
        val runnable = Runnable {
            unregisterUnlockCancel(context)
            fire(context, eventId, siren = true)
        }
        pending = runnable
        handler.postDelayed(runnable, graceSeconds.coerceAtLeast(0).toLong() * 1000L)
    }

    private fun fire(context: Context, eventId: String, siren: Boolean) {
        if (siren) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        // A live (armed) protection service attaches the photo and fix to the Motion event; otherwise
        // the guard's own text alert still goes out.
        if (!ProtectionService.captureForEvent(eventId, AlertKind.MOTION)) {
            AlertRuntime.alert(context, AlertKind.MOTION, eventId)
        }
    }

    /** The owner unlocking during the grace window cancels the whole response. */
    @Synchronized
    private fun registerUnlockCancel(context: Context) {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                cancel(ctx.applicationContext)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            unlockReceiver = receiver
        }
    }

    private fun unregisterUnlockCancel(context: Context) {
        unlockReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        unlockReceiver = null
    }

    /** Stops a pending grace response and silences the siren (owner unlocked, or a reset). */
    @Synchronized
    fun cancel(context: Context) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        unregisterUnlockCancel(context)
        Siren.stop(context)
    }
}
