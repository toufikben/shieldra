package com.shieldra.detection

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Batch A / A-45 D4: records what a real lift and a real snatch look like on the owner's handset, so
 * the Motion constants can be set from measurement instead of from a guess.
 *
 * The shipped rule judges how *hard* the phone is moved, and that number cannot tell a lift from a
 * bump — which is exactly why the sensitivity question is still open on this device
 * (`MotionCalibrationInstrumentedTest` prints `q_g1=UNANSWERED`). This harness measures the other fact
 * a single accelerometer gives: the *direction* gravity has turned through, printed next to the
 * magnitude so both can be compared against the same moment afterwards.
 *
 * It is a measurement, not a verification. Nothing here asserts anything about the device — no minimum
 * sample rate, no expected tilt, no threshold — because an assertion of that kind would turn a quiet
 * desk into a failing test and would be a detection policy written by whoever ran the harness. The only
 * judgement made is arithmetic on the samples that actually arrived, through [MotionTiltProbe].
 *
 * The operator's script, read out over adb while this runs:
 *   1. leave the phone lying still on the table for the whole rest window (8 s by default);
 *   2. lift it slowly to hand height, look at it, put it back;
 *   3. wait four seconds;
 *   4. take it off the table quickly and turn it over, the way a snatch is described.
 * Every sample after the rest window becomes one `SHIELDRA_LIFT` line, so the pause, the lift and the
 * snatch can be told apart in the log afterwards. Lines are stamped on the sensor's own clock
 * (`MotionSample.elapsedMs`), not the wall clock, because what is being recorded is the interval
 * between movements.
 *
 * Sampling runs on [MotionSensorAdapter]'s own collector thread and the instrumentation thread only
 * waits for the window — the arrangement slice G4-A measured as the one that survives on this device,
 * where a window closed by sleeps on the instrumentation thread can be starved of CPU and report
 * nothing at all. Window and rate are instrumentation arguments so a longer recording needs no rebuild:
 * `rest_ms`, `window_ms`, `period_us`.
 */
@RunWith(AndroidJUnit4::class)
class MotionLiftRecorderInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Everything the collector thread learns, published for the DONE line. */
    private class Recording {
        val restSamples: MutableList<DoubleArray> = Collections.synchronizedList(mutableListOf<DoubleArray>())

        @Volatile var firstSampleMs: Long? = null
        @Volatile var restSampleCount: Int = 0
        @Volatile var recordedLines: Int = 0
        @Volatile var baselineReady: Boolean = false
        @Volatile var baseline: DoubleArray? = null
        @Volatile var baselineNoiseSigma: Double = Double.NaN
        @Volatile var maxTiltDegrees: Double = 0.0
        @Volatile var maxDeviationMs2: Double = 0.0
    }

    @Test
    fun records_a_resting_pose_then_every_sample_until_the_window_closes() {
        val args = InstrumentationRegistry.getArguments()
        val restMs = args.longArg("rest_ms") ?: REST_MS
        val windowMs = args.longArg("window_ms") ?: RECORD_MS
        val periodUs = args.intArg("period_us") ?: REQUESTED_PERIOD_US

        val adapter = MotionSensorAdapter(sensorManager)
        val recording = Recording()
        val latch = CountDownLatch(1)

        val registration = adapter.startReporting({ sample -> onSample(sample, recording, restMs, windowMs, latch) }, periodUs)
        Log.i(TAG, "SHIELDRA_LIFT_START registration=$registration rest_ms=$restMs window_ms=$windowMs period_us=$periodUs")

        val windowClosed = latch.await(windowMs + LATCH_MARGIN_MS, TimeUnit.MILLISECONDS)
        val stopped = adapter.stopReporting()
        // Let an event already dispatched on the collector thread land before the counts are reported —
        // the same margin slice G4-A uses for the same reason.
        Thread.sleep(SETTLE_AFTER_STOP_MS)

        val restSnapshot = synchronized(recording.restSamples) { recording.restSamples.toList() }
        Log.i(
            TAG,
            "SHIELDRA_LIFT_DONE rest_samples=${recording.restSampleCount} recorded_lines=${recording.recordedLines} " +
                "baseline=${describe(recording.baseline)} sigma=${recording.baselineNoiseSigma} " +
                "sigma_recheck=${MotionTiltProbe.noiseSigma(restSnapshot)} " +
                "max_tilt_deg=${recording.maxTiltDegrees} max_deviation_ms2=${recording.maxDeviationMs2} " +
                "adapter_samples=${adapter.deliveredSampleCount} clock_drops=${adapter.clockResolutionDrops} " +
                "window_closed=$windowClosed stopped=$stopped",
        )
        if (recording.restSampleCount == 0) {
            // Not an assertion about the device: a recording with no resting pose has nothing to compare
            // against, so the operator is told this run produced nothing to analyse.
            Log.w(TAG, "SHIELDRA_LIFT_USELESS no rest samples arrived, so this run has no baseline to measure against")
        }
    }

    /** Runs on the collector thread. Logs one line per recorded sample and never throws. */
    private fun onSample(
        sample: MotionSample,
        recording: Recording,
        restMs: Long,
        windowMs: Long,
        latch: CountDownLatch,
    ) {
        val start = recording.firstSampleMs ?: sample.elapsedMs.also { recording.firstSampleMs = it }
        val elapsedMs = sample.elapsedMs - start
        val vector = doubleArrayOf(sample.x, sample.y, sample.z)
        val magnitude = MotionTiltProbe.magnitude(sample.x, sample.y, sample.z)

        if (elapsedMs < restMs) {
            recording.restSamples.add(vector)
            recording.restSampleCount = recording.restSamples.size
            return
        }

        if (!recording.baselineReady) {
            recording.baselineReady = true
            val resting = synchronized(recording.restSamples) { recording.restSamples.toList() }
            recording.baselineNoiseSigma = MotionTiltProbe.noiseSigma(resting)
            recording.baseline = MotionTiltProbe.restingBaseline(resting)
            Log.i(
                TAG,
                "SHIELDRA_LIFT_BASELINE n=${resting.size} ${describe(recording.baseline)} sigma=${recording.baselineNoiseSigma}",
            )
        }

        val baseline = recording.baseline
        val deviation = abs(magnitude - MotionTiltProbe.STANDARD_GRAVITY_MS2)
        recording.recordedLines++
        if (baseline == null) {
            // The rest window delivered nothing, so tilt has no reference. The magnitudes are still
            // worth having, and a run without them is better than a crash.
            Log.i(TAG, "SHIELDRA_LIFT t=$elapsedMs dev=$deviation tilt=NaN mag=$magnitude")
        } else {
            val tilt = MotionTiltProbe.angleDegrees(baseline, vector)
            recording.maxTiltDegrees = maxOf(recording.maxTiltDegrees, tilt)
            recording.maxDeviationMs2 = maxOf(recording.maxDeviationMs2, deviation)
            Log.i(TAG, "SHIELDRA_LIFT t=$elapsedMs dev=$deviation tilt=$tilt mag=$magnitude")
        }

        if (elapsedMs >= windowMs) latch.countDown()
    }

    private fun describe(baseline: DoubleArray?): String =
        if (baseline == null) "none" else "x=${baseline[0]} y=${baseline[1]} z=${baseline[2]}"

    private fun Bundle.longArg(name: String): Long? =
        getString(name)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    private fun Bundle.intArg(name: String): Int? =
        getString(name)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    private companion object {
        const val TAG = "ShieldraMotion"
        const val REST_MS = 8_000L
        const val RECORD_MS = 30_000L
        const val REQUESTED_PERIOD_US = 20_000

        /** The window is closed by the samples themselves; this is only the net under it. */
        const val LATCH_MARGIN_MS = 15_000L
        const val SETTLE_AFTER_STOP_MS = 500L
    }
}
