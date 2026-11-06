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
 * Runs the owner's response when the Motion guard confirms the phone was moved (A-33, back to its
 * one-shot shape after the A-45 layer was removed under design B on 2026-11-06).
 *
 * The owner picks one of three responses:
 *  - SILENT: capture a photo and a fix and send, quietly.
 *  - SIREN: the same, plus a loud alarm at once.
 *  - GRACE_THEN_SIREN: a few seconds' grace first (set by the owner) in which unlocking the phone
 *    cancels everything — so picking up your own phone does nothing — and only if no one unlocks does
 *    the alarm sound and the alert go out.
 *
 * The capture goes through the live [ProtectionService], so the photo and fix really attach to the
 * Motion incident; if protection is not armed, the guard keeps its own text-only alert. A movement that
 * joins an open incident runs this response again rather than going silent.
 *
 * What this deliberately no longer does, because the app holds no such power:
 *  - it never locks the screen: SHIELDRA holds no device administrator (design B);
 *  - it never posts a demand notice for a lock screen, because there is no lock of ours to post it on;
 *  - it runs no evidence ladder: the A-45 train existed to feed that notice, and a repeating camera and
 *    fix stream without a promise behind it is a cost, not a guard.
 */
object TableModeResponder {
    private const val TAG = "TableModeResponder"

    private val handler = Handler(Looper.getMainLooper())

    /** How long the siren is allowed to sound before it stops itself, if nothing else stops it. */
    private const val SIREN_MAX_MS = 30_000L

    /** The grace step of a waiting response. */
    private var pending: Runnable? = null

    private var unlockReceiver: BroadcastReceiver? = null

    fun onMotionConfirmed(context: Context, eventId: String) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        val plan = MotionResponsePlan.of(settings.motionResponse)
        if (plan.waitForOwnerUnlock) {
            scheduleAfterGrace(app, eventId, settings.motionGraceSeconds, plan)
        } else {
            fire(app, eventId, siren = plan.sirenNow)
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
            pending = null
            unregisterUnlockCancel(context)
            fire(context, eventId, siren = plan.sirenAfterGrace)
        }
        pending = runnable
        handler.postDelayed(runnable, graceSeconds.coerceAtLeast(0) * 1000L)
    }

    private fun fire(context: Context, eventId: String, siren: Boolean) {
        if (siren) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        // A live (armed) protection service attaches the photo and fix to the Motion incident, or keeps
        // the request for its next free moment; only when neither is possible does the guard's own text
        // alert go out, so the owner always hears about it.
        if (!ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)) {
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

    /** Stops a pending grace response and silences the siren (owner unlocked, or a disarm). */
    @Synchronized
    fun cancel(context: Context) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        unregisterUnlockCancel(context)
        Siren.stop(context)
    }
}
