package com.shieldra.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.shieldra.app.ShieldraApp
import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.Clock
import com.shieldra.domain.EventRepository
import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardSignal
import com.shieldra.domain.MotionConfirmation
import com.shieldra.domain.MotionIncidentBinder
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

    override val executionRunning: Boolean get() = execution?.phase == ExecutionPhase.Running
    override val sensorReporting: Boolean get() = adapter?.isReporting == true

    /** Nothing binds to this service; it is started, not held. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (applicationContext as? ShieldraApp)?.installedStorage?.eventRepository
        if (repository == null) {
            // Monitoring that cannot record what it confirms must not claim protection, and it does
            // not publish itself, so the UI keeps saying it is not monitoring.
            stopSelf()
            return START_NOT_STICKY
        }
        val controller = wire(repository)
        when (controller.start()) {
            is StartResult.Started, StartResult.AlreadyRunning -> MotionRuntime.publish(this)
            else -> {
                MotionRuntime.retract(this)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bringDown()
        MotionRuntime.retract(this)
        super.onDestroy()
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
                bringNotificationUp()
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
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notice)
        }
    }

    /** One delivered sample: bounded history first, then the judgement the owner approved. */
    private fun observe(sample: MotionSample) {
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
        if (confirmation is MotionConfirmation.Rejected) {
            Log.w(TAG, "a confirmed Movement could not be recorded: ${confirmation.reason}")
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
    }
}
