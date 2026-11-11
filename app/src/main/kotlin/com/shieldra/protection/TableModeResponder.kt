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
 * Inside a fresh safe zone the response keeps only its evidence: the siren stays silent and the message
 * is not sent, while the capture still runs (owner decision 2026-11-08). A thief who wants a signal must
 * take the phone out of the zone first, and that crossing is its own alert.
 *
 * A-46 (owner decision 2026-11-10) puts one more arm in front of that: a confirmed Motion that also
 * carries the measured snatch signature (A-47 D3) is treated as a snatch outside a safe zone, and
 * [SnatchCoordinator] locks the screen and opens the owner's ten-minute proof window. When it takes the
 * event, no photo or fix is taken here — evidence starts when the window ends — so this responder
 * returns instead of capturing. Movement without the signature, inside a safe zone, or with the snatch
 * guard switched off, keeps the evidence-only response below exactly as it was.
 */
object TableModeResponder {
    private const val TAG = "TableModeResponder"

    private val handler = Handler(Looper.getMainLooper())

    /** How long the siren is allowed to sound before it stops itself, if nothing else stops it. */
    private const val SIREN_MAX_MS = 30_000L

    /** The grace step of a waiting response. */
    private var pending: Runnable? = null

    private var unlockReceiver: BroadcastReceiver? = null

    fun onMotionConfirmed(context: Context, eventId: String, snatchSignature: Boolean) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        val plan = MotionResponsePlan.of(settings.motionResponse)
        if (plan.waitForOwnerUnlock) {
            scheduleAfterGrace(app, eventId, snatchSignature, settings.motionGraceSeconds, plan)
        } else {
            fire(app, eventId, snatchSignature, siren = plan.sirenNow)
        }
    }

    @Synchronized
    private fun scheduleAfterGrace(
        context: Context,
        eventId: String,
        snatchSignature: Boolean,
        graceSeconds: Int,
        plan: MotionResponsePlan,
    ) {
        cancel(context)
        registerUnlockCancel(context)
        val runnable = Runnable {
            pending = null
            unregisterUnlockCancel(context)
            fire(context, eventId, snatchSignature, siren = plan.sirenAfterGrace)
        }
        pending = runnable
        handler.postDelayed(runnable, graceSeconds.coerceAtLeast(0) * 1000L)
    }

    private fun fire(context: Context, eventId: String, snatchSignature: Boolean, siren: Boolean) {
        // A Motion confirmed inside a fresh safe zone is the owner at home or at work, not a snatch:
        // the siren and the message are nuisance there (owner decision 2026-11-08). The response is
        // read at fire time, after any grace window, so a phone that has left the zone by then is loud
        // again; the capture below still runs, because the event and its photo remain evidence.
        val quiet = ProtectionRuntime.quietInSafeZone(context)
        if (siren && !quiet) {
            Siren.start(context)
            handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
        }
        // A-46: outside a fresh safe zone a confirmed Motion that carried the measured snatch
        // signature (A-47 D3) is a snatch. The coordinator locks the screen and owns the event from
        // here; the camera waits for the ladder, because a thief running with the phone is not a
        // subject worth photographing at the second of the theft. Movement without the signature —
        // a lift, a walk, a ride — never reaches the coordinator: the challenge is for the measured
        // shape, not for the confirmation.
        if (snatchSignature && !quiet && SnatchCoordinator.begin(context, eventId)) return
        // A live (armed) protection service attaches the photo and fix to the Motion incident, or keeps
        // the request for its next free moment; only when neither is possible does the guard's own text
        // alert go out, so the owner always hears about it — unless the phone is quietly at home, where
        // there is nothing to hear.
        if (!ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)) {
            if (!quiet) AlertRuntime.alert(context, AlertKind.MOTION, eventId)
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
