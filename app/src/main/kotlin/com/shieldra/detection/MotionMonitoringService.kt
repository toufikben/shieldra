package com.shieldra.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.shieldra.app.ShieldraApp
import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.Clock
import com.shieldra.domain.EventRepository
import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardSignal
import com.shieldra.domain.MotionConfirmation
import com.shieldra.domain.MotionIncidentBinder
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.notification.LocalEventNotifier
import com.shieldra.runtime.BackgroundExecutionController
import com.shieldra.runtime.ExecutionPhase
import com.shieldra.runtime.MotionEventWrites
import com.shieldra.runtime.MotionMonitor
import com.shieldra.runtime.MotionRuntime
import com.shieldra.runtime.StartResult
import java.time.Clock as JavaClock
import java.time.Instant

/**
 * Batch G / slice G5: the foreground service that is Motion monitoring (amendments A-15, A-18).
 *
 * It is the only owner of [MotionSensorAdapter] in the shipping app (A-18 correction (أ)): one
 * registration, one `isReporting`, one teardown, so no two objects can disagree about whether the
 * accelerometer is live. What it publishes to [MotionRuntime] is the pair of facts the approved
 * truth is computed from — this service's own execution phase and its adapter's own registration —
 * never the fact that somebody pressed Start.
 *
 * The loop it runs is the composition the owner approved and nothing else: the samples the adapter
 * really delivered go through [MotionGuardPipeline] (stream verdict plus window judgement) into the
 * approved engine inside [MotionIncidentBinder]. It adds no threshold of its own; the two stream
 * limits it does apply are the approved numbers read back — a delivery gap beyond `maxSampleGapMs`
 * restarts history, because samples separated by a break cannot be judged as one stream, and
 * anything older than `motionRepetitionWindow` is dropped, because that window is all the
 * confirmation policy is allowed to look back at.
 *
 * A-9 fixes the notification wording while this service really runs: "SHIELDRA protection is
 * active". No "24/7", no "guaranteed", and no notice for a state that is not the one the user is
 * being told about — which is why a refused notification permission stops monitoring upstream at
 * [MotionMonitoringStartGate] rather than leaving a silent service running (A-18, Q-G5-b).
 */
class MotionMonitoringService : Service(), MotionMonitor {

    private var adapter: MotionSensorAdapter? = null
    private var execution: BackgroundExecutionController? = null
    private var pipeline: MotionGuardPipeline? = null
    private var binder: MotionIncidentBinder? = null

    /** Samples still inside the repetition window, oldest first. Touched on the delivery thread only. */
    private val samples = mutableListOf<MotionSample>()
    private val wallClock = Clock { Instant.now(JavaClock.systemUTC()) }

    /**
     * When the adapter last handed over a sample, on the monotonic clock. A registration the platform
     * accepted is not proof that samples arrive: many phones stop a non-wakeup accelerometer when the
     * screen goes dark, so "reporting" means a sample arrived within the repetition window — the only
     * span the approved confirmation rule can judge at all.
     */
    @Volatile
    private var lastDeliveryRealtimeMs: Long = 0L
    private val deliveryWatch = Handler(Looper.getMainLooper())
    private val deliveryCheck = object : Runnable {
        override fun run() {
            MotionRuntime.refresh()
            deliveryWatch.postDelayed(this, deliveryCheckIntervalMs)
        }
    }

    override val executionRunning: Boolean get() = execution?.phase == ExecutionPhase.Running
    override val sensorReporting: Boolean
        get() = adapter?.isReporting == true &&
            SystemClock.elapsedRealtime() - lastDeliveryRealtimeMs <= deliveryStaleAfterMs

    /** Nothing binds to this service; it is started, not held. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started with startForegroundService() must reach startForeground() on every path,
        // including the ones that stop at once; skipping it crashes the process on Android 8+.
        if (!raiseNotice()) {
            MotionRuntime.retract(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val storage = (applicationContext as? ShieldraApp)?.installedStorage
        val repository = storage?.eventRepository
        if (storage == null || repository == null) {
            // Monitoring that cannot record what it confirms must not claim protection, and it does
            // not publish itself, so the UI keeps saying it is not monitoring.
            stopWithoutNotice()
            return START_NOT_STICKY
        }
        // Q-K-2's second authorized execution point. Dispatched, never inline: the
        // sweep is a Room read and a Room write, and this callback is the main thread.
        storage.scheduleRetentionSweep()
        val controller = wire(repository)
        when (controller.start()) {
            is StartResult.Started, StartResult.AlreadyRunning -> {
                MotionRuntime.publish(this)
                deliveryWatch.removeCallbacks(deliveryCheck)
                deliveryWatch.postDelayed(deliveryCheck, deliveryCheckIntervalMs)
            }
            else -> {
                MotionRuntime.retract(this)
                stopWithoutNotice()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        deliveryWatch.removeCallbacks(deliveryCheck)
        bringDown()
        MotionRuntime.retract(this)
        super.onDestroy()
    }

    private fun raiseNotice(): Boolean = try {
        bringNotificationUp()
        true
    } catch (error: Exception) {
        Log.w(TAG, "the ongoing notice could not be raised: ${error.message}")
        false
    }

    private fun stopWithoutNotice() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Builds the adapter, the pipeline and the controller once, and only from approved numbers. */
    private fun wire(repository: EventRepository): BackgroundExecutionController {
        execution?.let { return it }
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val ownedAdapter = MotionSensorAdapter(sensorManager)
        val ownedPipeline = MotionGuardPipeline(
            calibration = ApprovedMotionCalibration.motionCalibration,
            stallToleranceFactor = ApprovedMotionCalibration.stallToleranceFactor,
        )
        val ownedBinder = MotionIncidentBinder(repository = repository, clock = wallClock)
        val controller = BackgroundExecutionController(
            bringUp = {
                // The notice is already up (onStartCommand raises it first); the grace period for the
                // first sample starts with the registration.
                lastDeliveryRealtimeMs = SystemClock.elapsedRealtime()
                val started = ownedAdapter.startReporting(
                    sink = { sample -> observe(sample) },
                    requestedReportPeriodUs = ApprovedMotionCalibration.requestedReportPeriodUs,
                )
                if (started != MotionStartResult.Started) {
                    throw IllegalStateException("the accelerometer was not registered: $started")
                }
            },
            tearDown = { ownedAdapter.stopReporting() },
        )
        adapter = ownedAdapter
        pipeline = ownedPipeline
        binder = ownedBinder
        execution = controller
        return controller
    }

    /**
     * The ongoing notice (A-9). It is raised before the registration so a platform that refuses the
     * service leaves nothing running, and a refusal throws out of `bringUp` into
     * [StartResult.Failed] rather than being swallowed.
     */
    private fun bringNotificationUp() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(com.shieldra.app.R.string.notification_channel_motion_monitoring),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(
                        com.shieldra.app.R.string.notification_channel_motion_monitoring_description,
                    )
                },
            )
        }
        val notice = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(getString(com.shieldra.app.R.string.notification_motion_ongoing_title))
            .setContentText(getString(com.shieldra.app.R.string.notification_motion_ongoing_text))
            .setOngoing(true)
            .apply { openAppIntent()?.let { setContentIntent(it) } }
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
    }

    /** Tapping the ongoing notice opens the app; stopping stays in the app, behind the owner's check. */
    private fun openAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    /** One delivered sample: bounded history first, then the judgement the owner approved. */
    private fun observe(sample: MotionSample) {
        lastDeliveryRealtimeMs = SystemClock.elapsedRealtime()
        val guardPipeline = pipeline ?: return
        val motionBinder = binder ?: return
        synchronized(samples) {
            val previous = samples.lastOrNull()
            if (previous != null && sample.elapsedMs - previous.elapsedMs > ApprovedMotionCalibration.maxSampleGapMs) {
                samples.clear()
            }
            samples += sample
            val horizon = sample.elapsedMs - ApprovedGuardRules.motionRepetitionWindow.toMillis()
            samples.removeAll { it.elapsedMs < horizon }
            val observation = guardPipeline.observe(samples.toList())
            // Only a movement the window actually found reaches the binder, so a resting device does
            // no storage work; the binder still owns the confirmation rule and the deduplication.
            if (observation.suspiciousOccurrences == 0) return
            val confirmation = motionBinder.confirm(
                GuardSignal(
                    kind = GuardKind.MOTION,
                    observedAt = wallClock.now(),
                    observation = observation,
                ),
            )
            // A confirmed movement is consumed. Left in the window it would be re-confirmed on every
            // following sample for up to ten seconds — hundreds of storage round trips a second on
            // this thread for one movement the binder had already recorded.
            if (confirmation is MotionConfirmation.Recorded || confirmation is MotionConfirmation.BoundToActive) {
                samples.clear()
            }
            report(confirmation)
        }
    }

    /**
     * What a confirmation means outside the monitor. A row that really got written invalidates the
     * event lists, so the owner sees the event the monitor just recorded without restarting the app
     * (G5 truthfulness fix B); a movement that joined the open incident wrote no row and so claims
     * no refresh. A confirmation that could not be stored is not a monitoring failure: the notice
     * stays truthful about monitoring while the recording problem is reported as what it is, which
     * is the only honest option left in this slice — the claim the user sees never comes from here.
     */
    private fun report(confirmation: MotionConfirmation) {
        MotionEventWrites.record(confirmation)
        when (confirmation) {
            // A new incident is what the owner started monitoring for, so it is announced. A movement
            // that joined the open incident is not announced again: one incident, one alert.
            is MotionConfirmation.Recorded -> {
                val recordedId = confirmation.event.id.value.toString()
                LocalEventNotifier(applicationContext).postRecordedAlert(
                    eventId = recordedId,
                    statusLabel = getString(com.shieldra.app.R.string.status_recorded),
                    title = getString(com.shieldra.app.R.string.notification_motion_event_title),
                )
                // A-32: the same incident goes to the owner's channels, silently and off this thread.
                AlertRuntime.alert(applicationContext, AlertKind.MOTION, recordedId)
            }
            is MotionConfirmation.Rejected ->
                Log.w(TAG, "a confirmed Movement could not be recorded: ${confirmation.reason}")
            else -> Unit
        }
    }

    private fun bringDown() {
        execution?.stop()
        synchronized(samples) { samples.clear() }
    }

    private companion object {
        const val TAG: String = "MotionMonitoringService"
        const val CHANNEL_ID: String = "shieldra_motion_monitoring"
        const val NOTIFICATION_ID: Int = 20_002

        /** Derived from the approved repetition window, never a new figure (see [lastDeliveryRealtimeMs]). */
        val deliveryStaleAfterMs: Long = ApprovedGuardRules.motionRepetitionWindow.toMillis()
        val deliveryCheckIntervalMs: Long = deliveryStaleAfterMs / 2
    }
}
