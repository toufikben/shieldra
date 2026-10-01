package com.shieldra.detection

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/**
 * Batch G / slice G3: the only Android sensor entry point amendment A-12 approves.
 *
 * The adapter owns registration and nothing else. It reports which motion sources this device has,
 * registers the **continuous accelerometer** when told to, converts each event into a [MotionSample]
 * stamped with the sensor's own clock, and stops when told to. It does not compute a rate, judge a
 * magnitude, confirm anything, or keep any state a process restart could inherit — the description
 * lives in [MotionSignalNormalizer] and the decision, when it is approved, will live above both.
 *
 * `SIGNIFICANT_MOTION` is deliberately *not* registered. It is a one-shot trigger sensor: the
 * platform refuses it through `registerListener` (measured on RMX3910 in slice G2) and arming it
 * belongs in its own layer, not fused into continuous sampling. Reporting its availability is the
 * most this slice is allowed to do with it.
 */
sealed interface MotionStartResult {
    /** The accelerometer is registered and events are being delivered to the caller's sink. */
    data object Started : MotionStartResult

    /** This device (as seen by this adapter) has no accelerometer, so nothing was registered. */
    data object NoAccelerometer : MotionStartResult

    /** The platform would not take the registration. Reported, not thrown and not swallowed. */
    data object RefusedByPlatform : MotionStartResult

    /** A stream was already running; starting a second one would leak the first listener. */
    data object AlreadyReporting : MotionStartResult
}

/**
 * @param requestedReportPeriodUs a *request*, in microseconds. Android treats the reporting period as
 * a suggestion and rate-limits motion sensors for API 31+ targets, so the delivered rate is measured
 * from timestamps downstream instead of assumed from this number. It has no default on purpose: a
 * default would be a rate decision made by an engineer rather than by the owner's calibration.
 */
class MotionSensorAdapter(
    private val sensorManager: SensorManager,
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
    private val significantMotion: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION),
) {

    val accelerometerAvailable: Boolean get() = accelerometer != null

    /** Availability only. This adapter never arms the tripwire, and the gate forbids the trigger API. */
    val significantMotionAvailable: Boolean get() = significantMotion != null

    /** The sensor this adapter actually registered, or null while nothing is registered. */
    @Volatile
    var registeredSensorType: Int? = null
        private set

    @Volatile
    var isReporting: Boolean = false
        private set

    /** Samples handed to the caller's sink. A sample dropped for clock reasons is not counted here. */
    @Volatile
    var deliveredSampleCount: Int = 0
        private set

    /**
     * Readings whose sensor timestamp, converted to milliseconds, collided with the previous one.
     * Slice G2 measured that a phone can batch events faster than the clock can name them, so this
     * is reported instead of being silently absorbed.
     */
    @Volatile
    var clockResolutionDrops: Int = 0
        private set

    @Volatile
    private var sink: ((MotionSample) -> Unit)? = null
    private var collectorThread: HandlerThread? = null
    @Volatile
    private var lastElapsedMs: Long? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
            val values = event.values
            if (values.size < 3) return
            val elapsedMs = event.timestamp / 1_000_000L
            val previous = lastElapsedMs
            if (previous != null && elapsedMs <= previous) {
                clockResolutionDrops++
                return
            }
            lastElapsedMs = elapsedMs
            deliveredSampleCount++
            sink?.invoke(
                MotionSample(
                    elapsedMs = elapsedMs,
                    x = values[0].toDouble(),
                    y = values[1].toDouble(),
                    z = values[2].toDouble(),
                ),
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun startReporting(sink: (MotionSample) -> Unit, requestedReportPeriodUs: Int): MotionStartResult {
        require(requestedReportPeriodUs > 0) { "A reporting period of $requestedReportPeriodUs µs requests nothing" }
        if (isReporting) return MotionStartResult.AlreadyReporting
        val sensor = accelerometer ?: return MotionStartResult.NoAccelerometer

        val thread = HandlerThread("shieldra-motion-adapter").also { it.start() }
        val handler = Handler(thread.looper)
        val accepted = sensorManager.registerListener(listener, sensor, requestedReportPeriodUs, handler)
        if (!accepted) {
            thread.quitSafely()
            return MotionStartResult.RefusedByPlatform
        }
        this.sink = sink
        collectorThread = thread
        lastElapsedMs = null
        registeredSensorType = sensor.type
        isReporting = true
        return MotionStartResult.Started
    }

    /**
     * Unregisters and ends the delivery thread. Safe to call twice and safe to call never; a new
     * process starts from an unregistered adapter because nothing here outlives the process.
     *
     * @return true if a live registration was ended.
     */
    fun stopReporting(): Boolean {
        if (!isReporting) return false
        sensorManager.unregisterListener(listener)
        collectorThread?.quitSafely()
        collectorThread = null
        sink = null
        lastElapsedMs = null
        registeredSensorType = null
        isReporting = false
        return true
    }
}
