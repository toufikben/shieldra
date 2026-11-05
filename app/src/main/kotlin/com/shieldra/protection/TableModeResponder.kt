package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.domain.SecurityEventType

/**
 * Runs the owner's response when the Motion guard confirms the phone was moved (A-33, restructured by
 * A-45 D1 and gated by the safe-zone policy of A-45 D2).
 *
 * The ladder, not one shot. At `t=0` the screen is taken and the holder is told, in the languages they
 * can read, what happens if nobody unlocks; the owner is alerted at once, because a text that arrives
 * now is worth more than a frame of a running thief. Only if nobody unlocks does the camera, the
 * microphone and the fix start being spent — at 30 s, then every 60 s five times, then every 5 min
 * until 30 min from the incident. A movement that binds to the open incident is not silence either: if
 * the train already runs it keeps its cadence, and if it settled or was cancelled, the movement starts
 * a new one.
 *
 * Three things are preserved from before and are pinned by the g4b review:
 *  - the lock goes through [DeviceScreenLock] and only while the stored armed flag says protection is
 *    on, so a late ladder step can never lock a phone its owner stopped watching;
 *  - the grace response is still the one response whose lock waits for the owner's own number
 *    ([ProtectionSettings.motionGraceSeconds]) instead of the ladder's 30 s — what A-45 changes is that
 *    the wait is now visible on the lock screen, which is what made it unusable before;
 *  - evidence still goes through the live [ProtectionService] so the photo and the fix attach to the
 *    Motion incident, and the guard's own text alert remains the fallback when nothing can take it.
 *
 * The demand notice is shown only together with a lock and only while protection is armed: it promises
 * the owner will receive the phone's location and pictures, and a disarmed monitor cannot deliver that,
 * so an unarmed Motion start keeps the single response it had before rather than claiming more.
 */
object TableModeResponder {
    private const val TAG = "TableModeResponder"

    private val handler = Handler(Looper.getMainLooper())

    /** How long the siren is allowed to sound before it stops itself, if nothing else stops it. */
    private const val SIREN_MAX_MS = 30_000L

    /** The next evidence burst of the running ladder, and the grace step of a waiting response. */
    @Volatile
    private var burstStep: Runnable? = null

    @Volatile
    private var graceStep: Runnable? = null

    private var unlockReceiver: BroadcastReceiver? = null

    @Volatile
    private var incidentStartedAtMs = 0L

    @Volatile
    private var burstsDone = 0

    /** The incident the running ladder is spending evidence for. */
    @Volatile
    private var incidentEventId: String? = null

    @Synchronized
    fun onMotionConfirmed(context: Context, eventId: String) {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        val plan = MotionResponsePlan.of(settings.motionResponse)
        val response = zoneResponse(app, settings)
        if (response == ZoneResponse.MUTED) {
            Log.i(
                TAG,
                "the confirmed Motion is inside a safe zone with the phone charging: recorded, not escalated",
            )
            return
        }
        if (burstStep != null) {
            Log.i(TAG, "a movement joined the incident that is already escalating; the running ladder keeps its cadence")
            return
        }
        if (!settings.armed) {
            respondOnce(app, settings, plan, eventId)
            return
        }
        cancelSteps(app)
        if (response == ZoneResponse.QUIET) {
            respondQuietly(app, settings, plan, eventId)
        } else {
            runLadder(app, settings, plan, eventId)
        }
    }

    /**
     * D2: a zone mutes escalation and never detection, and an unknown position is treated as outside,
     * because a missing permission or an old fix must not turn the guard off.
     */
    private fun zoneResponse(context: Context, settings: ProtectionSettings): ZoneResponse {
        val zonesExist = settings.geofenceEnabled && ProtectionSettingsStore(context).safeZones().isNotEmpty()
        val sample = ZoneStateStore(context).read()
        val staleAfterMs = GeofenceTicker.staleAfterMs(settings.geofenceIntervalMinutes)
        return ZonePolicy.responseFor(
            zonesExist = zonesExist,
            stateKnown = sample != null && sample.isFresh(System.currentTimeMillis(), staleAfterMs),
            inside = sample?.inside ?: false,
            charging = BatteryLevel.isCharging(context),
        )
    }

    /** The full ladder (D1): lock and demand now, first burst at 30 s, then the widening train. */
    private fun runLadder(
        context: Context,
        settings: ProtectionSettings,
        plan: MotionResponsePlan,
        eventId: String,
    ) {
        incidentStartedAtMs = SystemClock.elapsedRealtime()
        incidentEventId = eventId
        burstsDone = 0

        // The owner hears now; the holder is told now; the camera is deliberately not part of this step.
        AlertRuntime.alert(context, AlertKind.MOTION, eventId)
        if (plan.waitForOwnerUnlock) {
            scheduleGrace(context, settings, plan)
        } else {
            lockTheScreen(context)
        }
        TheftDemand.show(context, settings.ownerContact, settings.lostMessage)
        registerUnlockCancel(context)
        if (plan.sirenNow) soundSiren(context)
        scheduleNextBurst(context)
    }

    /**
     * Inside a zone and unplugged: the phone is still taken and the owner still gets one piece of
     * evidence, while the parts that make noise — the repeating train and the siren — stay muted. The
     * notice goes with the lock, never instead of it: an unexplained lock is the failure A-45 D3 exists
     * to fix, and a notice without a lock is the nuisance the owner asked to be muted at home.
     */
    private fun respondQuietly(
        context: Context,
        settings: ProtectionSettings,
        plan: MotionResponsePlan,
        eventId: String,
    ) {
        Log.i(TAG, "the confirmed Motion is inside a safe zone: escalating quietly, no burst train")
        AlertRuntime.alert(context, AlertKind.MOTION, eventId)
        if (plan.lockAtOnce) {
            lockTheScreen(context)
            TheftDemand.show(context, settings.ownerContact, settings.lostMessage)
            registerUnlockCancel(context)
        }
        requestCapture(context, eventId)
    }

    /**
     * Protection is disarmed, so this is the dashboard's own Motion monitor answering: one response, on
     * the owner's chosen terms, with no demand notice and no train. The lock it asks for is refused by
     * [lockTheScreen] on the armed flag.
     */
    private fun respondOnce(
        context: Context,
        settings: ProtectionSettings,
        plan: MotionResponsePlan,
        eventId: String,
    ) {
        if (!plan.waitForOwnerUnlock) {
            if (plan.sirenNow) soundSiren(context)
            requestCapture(context, eventId)
            return
        }
        registerUnlockCancel(context)
        val runnable = Runnable {
            graceStep = null
            unregisterUnlockCancel(context)
            if (plan.sirenAfterGrace) soundSiren(context)
            requestCapture(context, eventId)
        }
        graceStep = runnable
        handler.postDelayed(runnable, settings.motionGraceSeconds.coerceAtLeast(0) * 1000L)
    }

    /** The grace response's own step: the lock lands on the owner's number, and only if they did not unlock. */
    private fun scheduleGrace(
        context: Context,
        settings: ProtectionSettings,
        plan: MotionResponsePlan,
    ) {
        val runnable = Runnable {
            graceStep = null
            if (!ProtectionSettingsStore(context).load().armed) return@Runnable
            if (plan.lockAfterGrace) lockTheScreen(context)
            if (plan.sirenAfterGrace) soundSiren(context)
        }
        graceStep = runnable
        handler.postDelayed(runnable, settings.motionGraceSeconds.coerceAtLeast(0) * 1000L)
    }

    /**
     * Schedules the next burst from the ladder's own clock. Nothing is scheduled past the window, and a
     * step that finds the phone inside a safe zone stops the train there rather than waking the house.
     */
    private fun scheduleNextBurst(context: Context) {
        val elapsedMs = SystemClock.elapsedRealtime() - incidentStartedAtMs
        val tick = SnatchLadder.next(elapsedMs, burstsDone)
        if (tick.action == LadderAction.SETTLED) {
            Log.i(TAG, "the snatch ladder settled after ${tick.burst} burst(s)")
            return
        }
        val runnable = Runnable {
            burstStep = null
            val eventId = incidentEventId
            if (eventId == null) return@Runnable
            val settings = ProtectionSettingsStore(context).load()
            if (!settings.armed) {
                Log.i(TAG, "the snatch ladder stopped because protection is no longer armed")
                return@Runnable
            }
            when (zoneResponse(context, settings)) {
                ZoneResponse.MUTED -> {
                    Log.i(TAG, "the snatch ladder stopped: the phone is back in a charging safe zone")
                    return@Runnable
                }
                ZoneResponse.QUIET -> {
                    requestCapture(context, eventId)
                    Log.i(TAG, "the snatch ladder took one quiet burst inside a safe zone and stopped the train")
                    return@Runnable
                }
                ZoneResponse.FULL -> Unit
            }
            burstsDone = tick.burst
            requestCapture(context, eventId)
            scheduleNextBurst(context)
        }
        burstStep = runnable
        handler.postDelayed(runnable, tick.delayMs)
    }

    /**
     * One step of evidence. A live service attaches the photo and the fix to the incident, or keeps the
     * request for its next free moment; only when neither is possible does the guard's own text alert go
     * out, so the owner always hears about it.
     */
    private fun requestCapture(context: Context, eventId: String) {
        if (!ProtectionService.captureForEvent(eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)) {
            AlertRuntime.alert(context, AlertKind.MOTION, eventId)
        }
    }

    /**
     * Locks only while protection is really armed: a response queued by the grace or by a burst outlives
     * a disarm, and the owner's decision to stop watching must also stop the screen locking itself.
     */
    private fun lockTheScreen(context: Context) {
        if (!ProtectionSettingsStore(context).load().armed) return
        val result = DeviceScreenLock.now(context)
        if (result != DeviceScreenLock.Result.LOCKED) {
            Log.w(TAG, "the confirmed Motion could not lock the screen: $result")
        }
    }

    private fun soundSiren(context: Context) {
        Siren.start(context)
        handler.postDelayed({ Siren.stop(context) }, SIREN_MAX_MS)
    }

    /** Any owner unlock ends the response: the holder proved they are the owner. */
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

    private fun cancelSteps(context: Context) {
        burstStep?.let { handler.removeCallbacks(it) }
        burstStep = null
        graceStep?.let { handler.removeCallbacks(it) }
        graceStep = null
    }

    /** Stops a running ladder, silences the siren and takes the demand off the lock screen. */
    @Synchronized
    fun cancel(context: Context) {
        cancelSteps(context)
        unregisterUnlockCancel(context)
        burstsDone = 0
        incidentStartedAtMs = 0L
        incidentEventId = null
        Siren.stop(context)
        TheftDemand.hide(context)
    }
}
