package com.shieldra.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.app.MainActivity
import com.shieldra.domain.SensorQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Batch G / slice G2: the evidence amendment A-8 asks for before any sensor adapter, foreground
 * service, or service type is chosen.
 *
 * The suite does two different things and the difference is deliberate:
 * - it **asserts** only what must hold on any device that could support the approved G1-02 rule
 *   (the sensor exists, a resumed app receives events, delivered samples are well formed, and the
 *   pure evaluator treats live samples by the same rule its JVM suite tests);
 * - it **measures and logs** the quantities that are decisions rather than facts: whether delivery
 *   survives with no resumed activity (that is what tells us if a foreground service is needed),
 *   and what the resting deviation looks like on this phone (that is the input to Q-G1, which the
 *   owner answers).
 *
 * `FIXTURE_CALIBRATION` is a test-only fixture mirroring the JVM suite's. Nothing here ships a
 * threshold.
 */
@RunWith(AndroidJUnit4::class)
class MotionSensorEvidenceInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: SensorManager = context.getSystemService(SensorManager::class.java)

    @Test
    fun the_accelerometer_the_approved_rule_needs_is_present_and_its_limits_are_recorded() {
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        assertNotNull(
            "G1-02 cannot run at all without an accelerometer, and this device reported none",
            accelerometer,
        )
        if (accelerometer != null) {
            Log.i(
                TAG,
                "MEASURED accelerometer name=${accelerometer.name} vendor=${accelerometer.vendor} " +
                    "maximumRange=${accelerometer.maximumRange} minDelayUs=${accelerometer.minDelay} " +
                    "maxDelayUs=${accelerometer.maxDelay} powerMa=${accelerometer.power} " +
                    "wakeUp=${accelerometer.isWakeUpSensor} reportingMode=${accelerometer.reportingMode}",
            )
            // A burst has to be sampled often enough that "three seconds" means anything. The
            // datasheet is not evidence; this runtime value is.
            assertTrue(
                "the accelerometer must be able to sample at least every 200 ms, minDelayUs=" +
                    accelerometer.minDelay,
                accelerometer.minDelay <= 200_000,
            )
        }
    }

    @Test
    fun a_resumed_app_receives_accelerometer_events() {
        val accelerometer = requireAccelerometer()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val burst: Burst
        try {
            burst = sampleFor(accelerometer, SENSOR_PERIOD_US, WINDOW_MS)
        } finally {
            scenario.close()
        }
        val events = burst.samples
        Log.i(TAG, "MEASURED foreground_delivery_events=${burst.deliveredCount} in ${WINDOW_MS} ms")
        assertTrue(
            "a visible Shieldra must receive sensor events or the Motion rule cannot be evaluated " +
                "at all; measured ${events.size} events in ${WINDOW_MS} ms",
            events.size >= 2,
        )
        assertWellFormed(events)
    }

    @Test
    fun sensor_delivery_with_no_resumed_activity_is_measured_because_it_decides_amendment_a_8() {
        val accelerometer = requireAccelerometer()
        // No activity is launched here. This is the closest a JUnit4 instrumentation test can get
        // to "the app is running with nothing on screen", and the instrumentation itself keeps the
        // process alive, so the count is an **upper bound** on real background delivery, not proof
        // of it. Both outcomes are legitimate answers, which is why the number is logged and read
        // in the batch report instead of asserted as a pass condition.
        val burst = sampleFor(accelerometer, SENSOR_PERIOD_US, WINDOW_MS)
        Log.i(
            TAG,
            "MEASURED unresumed_delivery_events=${burst.deliveredCount} in ${WINDOW_MS} ms " +
                "(upper bound: instrumentation drives the process during this test)",
        )
        assertWellFormed(burst.samples)
    }

    @Test
    fun the_significant_motion_tripwire_is_a_one_shot_wake_source_as_the_requirements_claim() {
        val tripwire = manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        assertNotNull(
            "the measured inventory reported SIGNIFICANT_MOTION; without it the wake-up tripwire " +
                "design in docs/capabilities/motion-detection-requirements.md does not hold",
            tripwire,
        )
        if (tripwire != null) {
            Log.i(
                TAG,
                "MEASURED significant_motion name=${tripwire.name} wakeUp=${tripwire.isWakeUpSensor} " +
                    "reportingMode=${tripwire.reportingMode} " +
                    "fifoMaxEventCount=${tripwire.fifoMaxEventCount} minDelayUs=${tripwire.minDelay}",
            )
            // These two properties are the whole design: wakeUp is what lets a still phone be woken
            // by movement at all, and one-shot is what makes one registration cost one event rather
            // than a stream to keep switching off.
            assertTrue(
                "a tripwire that cannot wake the device cannot restart sensing after the process " +
                    "stopped, and this one reported wakeUp=false",
                tripwire.isWakeUpSensor,
            )
            assertEquals(
                "a continuous tripwire would deliver a stream of events to filter instead of one " +
                    "per registration; this device reported reportingMode=" + tripwire.reportingMode,
                Sensor.REPORTING_MODE_ONE_SHOT,
                tripwire.reportingMode,
            )
        }
    }

    @Test
    fun the_tripwire_delivers_at_most_one_event_per_registration_while_the_phone_is_untouched() {
        val tripwire = requireSignificantMotion()
        val burst = sampleFor(tripwire, SensorManager.SENSOR_DELAY_NORMAL, WINDOW_MS)
        assertTrue(
            "SIGNIFICANT_MOTION is a one-shot sensor; more than one event per registration means " +
                "this vendor HAL is not the platform's own tripwire, measured ${burst.deliveredCount}",
            burst.deliveredCount <= 1,
        )
        // Whether it fires at all while nobody touches the phone is a measurement, not a claim:
        // zero here is expected on a still device and proves nothing about a real carry.
        Log.i(
            TAG,
            "MEASURED significant_motion_fired_untouched=${burst.deliveredCount} in ${WINDOW_MS} ms",
        )
    }

    @Test
    fun real_samples_are_judged_by_the_same_rule_the_jvm_suite_tests() {
        val accelerometer = requireAccelerometer()
        val rest = sampleFor(accelerometer, SENSOR_PERIOD_US, REST_WINDOW_MS).samples
        reportRestingDeviation(rest)

        when (val reading = MotionWindowEvaluator(FIXTURE_CALIBRATION).read(rest)) {
            MotionReading.Insufficient -> assertTrue(
                "fewer than two live samples must read as Insufficient, never as a still device; " +
                    "measured ${rest.size} samples",
                rest.size < 2,
            )

            is MotionReading.Unreliable -> {
                // Legitimate on a loaded device: the harness itself stalled. The rule must still
                // refuse to upgrade it.
                Log.i(TAG, "MEASURED rest_reading=Unreliable reason=${reading.reason}")
                assertTrue(
                    "an Unreliable live reading must map to NOISY so the approved rule cannot " +
                        "confirm from it",
                    MotionGuardObservation.of(reading).sensorQuality == SensorQuality.NOISY,
                )
            }

            is MotionReading.Movements -> {
                assertWellFormed(rest)
                reading.episodes.forEach { episode ->
                    assertTrue(
                        "an episode built from live samples shorter than minEpisodeMs must have " +
                            "been dropped, was ${episode.durationMs} ms",
                        episode.durationMs >= FIXTURE_CALIBRATION.minEpisodeMs,
                    )
                }
                assertTrue(
                    "the bridge must never upgrade live samples above VALID, was " +
                        MotionGuardObservation.of(reading).sensorQuality,
                    MotionGuardObservation.of(reading).sensorQuality == SensorQuality.VALID,
                )
            }
        }
    }

    /**
     * Everything the harness collects must be usable: finite axes and a time that never runs
     * backwards. Two samples inside the same millisecond are allowed here (the stricter rule is
     * the evaluator's own, and the test above checks what it decided); a backwards clock is not.
     */
    private fun assertWellFormed(events: List<MotionSample>) {
        events.forEachIndexed { index, sample ->
            assertTrue(
                "sensor values must be finite, was (${sample.x}, ${sample.y}, ${sample.z})",
                sample.x.isFinite() && sample.y.isFinite() && sample.z.isFinite(),
            )
            if (index > 0) {
                assertTrue(
                    "sensor timestamps never move backwards, ${events[index - 1].elapsedMs} then " +
                        "${sample.elapsedMs}",
                    sample.elapsedMs >= events[index - 1].elapsedMs,
                )
            }
        }
    }

    /** Median, min and max deviation at rest — the numbers Q-G1 needs, measured not assumed. */
    private fun reportRestingDeviation(events: List<MotionSample>) {
        if (events.isEmpty()) {
            Log.i(TAG, "MEASURED rest_samples=0 (nothing was delivered to measure)")
            return
        }
        val deviations = events.map { deviationOf(it) }.sorted()
        val middle = deviations.size / 2
        val median = if (deviations.size % 2 == 1) {
            deviations[middle]
        } else {
            (deviations[middle - 1] + deviations[middle]) / 2.0
        }
        Log.i(
            TAG,
            "MEASURED rest_samples=${events.size} deviation_min=${deviations.first()} " +
                "deviation_median=$median deviation_max=${deviations.last()} " +
                "span_ms=${events.last().elapsedMs - events.first().elapsedMs}",
        )
    }

    private fun deviationOf(sample: MotionSample): Double =
        abs(sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z) - GRAVITY_MS2)

    private fun requireAccelerometer(): Sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ?: throw AssertionError("the approved Motion rule needs an accelerometer and this device has none")

    private fun requireSignificantMotion(): Sensor =
        manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            ?: throw AssertionError("the wake-up tripwire design needs SIGNIFICANT_MOTION and this device has none")

    private fun sampleFor(sensor: Sensor, samplingPeriodUs: Int, windowMs: Long): Burst {
        val thread = HandlerThread("shieldra-motion-evidence").apply { start() }
        val collector = Collector()
        try {
            val handler = Handler(thread.looper)
            assertTrue(
                "the platform refused to register ${sensor.name}; nothing can be measured",
                manager.registerListener(collector, sensor, samplingPeriodUs, 0, handler),
            )
            val finished = CountDownLatch(1)
            handler.postDelayed({ finished.countDown() }, windowMs)
            finished.await(windowMs + 2_000, TimeUnit.MILLISECONDS)
            return Burst(deliveredCount = collector.deliveredCount, samples = collector.samples.toList())
        } finally {
            manager.unregisterListener(collector)
            thread.quitSafely()
        }
    }

    /**
     * What one registration produced. [deliveredCount] counts every event; [samples] holds only
     * readings that really carry three axes, because a one-shot sensor such as SIGNIFICANT_MOTION
     * documents a single value and the array it hands a listener is not always three long.
     */
    private class Burst(val deliveredCount: Int, val samples: List<MotionSample>)

    /** Raw events become the pure domain sample type, on the handler thread. */
    private class Collector : SensorEventListener {
        val samples: MutableList<MotionSample> = Collections.synchronizedList(mutableListOf())
        @Volatile
        var deliveredCount: Int = 0
            private set

        override fun onSensorChanged(event: SensorEvent) {
            deliveredCount++
            val values = event.values
            if (values.size < 3) return
            samples += MotionSample(
                elapsedMs = event.timestamp / 1_000_000L,
                x = values[0].toDouble(),
                y = values[1].toDouble(),
                z = values[2].toDouble(),
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        const val TAG = "ShieldraMotion"

        /** 50 Hz: well inside this device's measured 10–200 Hz accelerometer range. */
        const val SENSOR_PERIOD_US = 20_000
        const val WINDOW_MS = 1_500L
        const val REST_WINDOW_MS = 2_000L
        const val GRAVITY_MS2 = 9.80665

        val FIXTURE_CALIBRATION = MotionCalibration(
            strongDeltaMs2 = 4.0,
            noiseFloorDeltaMs2 = 1.2,
            minEpisodeMs = 400,
            maxSampleGapMs = 1_000,
        )
    }
}
