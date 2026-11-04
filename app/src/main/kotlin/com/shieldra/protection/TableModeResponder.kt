package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.domain.SecurityEventType

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
 *
 * A-43 adds the owner's second request: a confirmed lift also locks the screen, with the same
 * credentialed administrator A-42 introduced. The grace response is the one exception to "at once":
 * the owner's own unlock during the grace cancels the siren and the lock together, so picking your
 * phone up does not lock you out. The lock is refused, and only logged, when protection was disarmed
 * between the confirmation and the response.
 */
object TableModeResponder {
    private const val TAG = "TableModeResponder"

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var unlockReceiver: BroadcastReceiver? = null

    /** How long the siren is allowed to sound before it stops itself, if nothing else stops it. */
    private const val SIREN_MAX_MS = 30_000L

    fun onMotionConfirmed(context: Context, eventId: String) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        val plan = MotionResponsePlan.of(settings.motionResponse)
        if (plan.waitForOwnerUnlock) {
            scheduleAfterGrace(app, eventId, settings.motionGraceSeconds, plan)
        } else {
            fire(app, eventId, siren = plan.sirenNow, lock = plan.lockAtOnce)
        }
    }

    @Synchronized
    private fun scheduleAfterGrace(
        context: Context,
        eventId: String,
        graceSeconds: Int,
        plan: MotionResponsePlan,
    ) {
        cancel(context)
        registerUnlockCancel(context)
        val runnable = Runnable {
            unregisterUnlockCancel(context)
            fire(context, eventId, siren = plan.sirenAfterGrace, lock = plan.lockAfterGrace)
        }
        pending = runnable
        handler.postDelayed(runnable, graceSeconds.coerceAtLeast(0).toLong() * 1000L)
    }

    private fun fire(context: Context, eventId: String, siren: Boolean, lock: Boolean) {
        if (siren) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        // A live (armed) protection service attaches the photo and fix to the Motion event, or keeps the
        // request for its next free moment; only when neither is possible does the guard's own text
        // alert go out, so the owner always hears about it.
        if (!ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)) {
            AlertRuntime.alert(context, AlertKind.MOTION, eventId)
        }
        // After the capture request, never before it: the camera and the fix need the moment the
        // confirmation bought them. The screen lock then denies the phone to whoever is holding it.
        if (lock) lockTheScreen(context)
    }

    /**
     * Locks only while protection is really armed: a response queued by the grace outlives a disarm,
     * and the owner's decision to stop watching must also stop the screen from locking itself.
     */
    private fun lockTheScreen(context: Context) {
        if (!ProtectionSettingsStore(context).load().armed) return
        val result = DeviceScreenLock.now(context)
        if (result != DeviceScreenLock.Result.LOCKED) {
            Log.w(TAG, "the confirmed Motion could not lock the screen: $result")
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
