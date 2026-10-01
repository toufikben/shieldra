package com.shieldra.detection

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import kotlin.math.ceil

/**
 * Batch G / slice Q-G1: the one deliberate movement the owner's A-15 directive authorizes, measured
 * and reported — never converted into a threshold.
 *
 * What this file is allowed to be is stated precisely in that directive. It samples through
 * [MotionSensorAdapter], the Android entry point slice G3 already verified on this device, so no new
 * registration path appears. It describes each reading with [MotionSignalNormalizer], the shipping
 * signal layer, so the magnitude and the gravity deviation in these lines are the same numbers the
 * product would compute. It creates no Security Event, no alert, no notification, no stored row and
 * no file, and it touches no camera, no location and no channel. And it never moves the phone: the
 * pickup, the carry and the return are done by the device owner with their hands, in the timed
 * sequence the directive's §5 sets out.
 *
 * The raw/derived split is what keeps this a calibration *input* rather than a decision. Each `RAW`
 * line is a reading the sensor delivered, with its own timestamp. Each `DERIVED` line is a summary
 * computed afterwards from those readings. The only levels inside the code are named `PROBE`: they
 * locate which stretch of the window was the carry, and a probe is a searchlight, not a product
 * level. The shipping strong-motion level is Q-G1 — the owner answers it from the reported ranges,
 * so nothing here may be cited later as if it had been answered.
 */
@RunWith(AndroidJUnit4::class)
class MotionCalibrationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * §7-A supplement: the stationary deviation, measured now on the shipping path with the §6 field
     * list attached. The G2/G3 rest numbers stay valid; this run adds to them and replaces nothing.
     */
    @Test
    fun the_rest_phase_reports_every_measured_field_without_any_movement_being_made() {
        val started = System.currentTimeMillis()
        val stream = measure(REST_ONLY_WINDOW_MS)
        assertEquals(
            "the harness measures through the verified adapter, so a registration this device refused " +
                "must be reported rather than measured around: ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )

        Log.i(TAG, "ENVIRONMENT kind=rest_only device=${Build.MODEL} product=${Build.DEVICE} " +
            "android_release=${Build.VERSION.RELEASE} android_sdk=${Build.VERSION.SDK_INT} " +
            "requested_report_period_us=$REQUESTED_REPORT_PERIOD_US " +
            "screen_interactive_at_end=${powerManager.isInteractive} " +
            "adapter_delivered=${stream.deliveredSampleCount} clock_resolution_drops=${stream.clockResolutionDrops}")

        val signal = MotionSignalNormalizer(STALL_TOLERANCE_FACTOR).normalize(stream.samples)
        reportRaw(PHASE_REST, signal.points)
        reportDerived(summarize(PHASE_REST, signal, stream.clockResolutionDrops))

        assertTrue(
            "a still device must be measured, not assumed: ${stream.samples.size} samples in $REST_ONLY_WINDOW_MS ms",
            stream.samples.size >= 2,
        )
        assertNotNull("the interval must be measured from the timestamps that arrived", signal.medianIntervalMs)
        assertTrue(
            "every reading must be a finite acceleration, quality was ${signal.quality}",
            signal.points.all { it.gravityDeltaMs2.isFinite() },
        )
    }

    /**
     * §5 sequence, one pass: leave it still → pick it up deliberately → carry it naturally → put it
     * back down → the window ends by itself. The `INSTRUCTION` lines are printed before sampling
     * starts; the phases are wall-clock windows and the sensor keeps delivering through all three.
     */
    @Test
    fun one_deliberate_carry_is_measured_against_the_rest_baseline_of_the_same_run() {
        val phases = listOf(PHASE_REST to PHASE_REST_MS, PHASE_CARRY to PHASE_CARRY_MS, PHASE_SETTLE to PHASE_SETTLE_MS)
        phases.forEach { (name, durationMs) ->
            Log.i(TAG, "INSTRUCTION phase=$name seconds=${durationMs / 1000} action=${instructionFor(name)}")
        }

        val started = System.currentTimeMillis()
        val stream = measure(PHASE_REST_MS + PHASE_CARRY_MS + PHASE_SETTLE_MS)
        assertEquals(
            "the carry sequence needs the verified adapter's registration, was ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )

        Log.i(TAG, "ENVIRONMENT kind=carry_sequence device=${Build.MODEL} product=${Build.DEVICE} " +
            "android_release=${Build.VERSION.RELEASE} android_sdk=${Build.VERSION.SDK_INT} " +
            "requested_report_period_us=$REQUESTED_REPORT_PERIOD_US window_ms=${System.currentTimeMillis() - started} " +
            "screen_interactive_at_end=${powerManager.isInteractive} " +
            "adapter_delivered=${stream.deliveredSampleCount} clock_resolution_drops=${stream.clockResolutionDrops} " +
            "first_reading_after_launch_ms=${stream.arrived.minOfOrNull { it.arrivedAtMs }?.minus(started) ?: -1}")

        // Phases are located by arrival time, which is the only clock that can be compared with the
        // window start; sensor time lives in a different epoch and is reported, never aligned to it.
        // The anchor is the first reading the sensor actually delivered, because the activity launch
        // before it is not part of the §5 sequence.
        val anchor = stream.arrived.minOfOrNull { it.arrivedAtMs } ?: started
        val byPhase = phases.associate { (name, _) ->
            name to stream.arrived.filter { phaseOf(it.arrivedAtMs - anchor, phases) == name }.map { it.sample }
        }

        fun measured(name: String): PhaseSummary =
            summarize(name, MotionSignalNormalizer(STALL_TOLERANCE_FACTOR).normalize(byPhase.getValue(name)), stream.clockResolutionDrops)

        val rest = measured(PHASE_REST)
        val carry = measured(PHASE_CARRY)
        val settle = measured(PHASE_SETTLE)

        listOf(rest, carry, settle).forEach { summary ->
            reportRaw(summary.phase, summary.points)
            reportDerived(summary)
        }

        val probe = rest.deviationMax * PROBE_REST_MULTIPLIER
        Log.i(TAG, "PROBE rest_deviation_max=${rest.deviationMax} multiplier=$PROBE_REST_MULTIPLIER " +
            "probe_deviation=$probe purpose=locate_which_stretch_was_the_carry NOT_a_product_level")

        val runMs = longestRunAboveMs(carry.points, probe)
        Log.i(TAG, "deliberate_movement_identified=${runMs >= MIN_CARRY_RUN_MS} longest_run_above_probe_ms=$runMs " +
            "required_run_ms=$MIN_CARRY_RUN_MS probe_deviation=$probe " +
            "carry_deviation_max=${carry.deviationMax} carry_sample_count=${carry.inputCount} " +
            "settle_deviation_max=${settle.deviationMax}")
        PROBE_LEVELS_MS2.forEach { level ->
            Log.i(TAG, "PROBE_LEVEL level=$level above_count=${carry.points.count { it.gravityDeltaMs2 > level }} " +
                "longest_run_ms=${longestRunAboveMs(carry.points, level)}")
        }
        Log.i(TAG, "OWNERSHIP q_g1=UNANSWERED shipped_strong_threshold=false measured_only=TRUE")

        assertTrue("the carry window must itself have been sampled: ${carry.inputCount} samples", carry.inputCount >= 2)
        assertTrue("the rest phase must anchor the same-run comparison: ${rest.inputCount} samples", rest.inputCount >= 2)
        assertTrue("a probe from a measured rest maximum must be a finite positive number, was $probe", probe.isFinite() && probe > 0.0)
    }

    private fun instructionFor(phase: String): String = when (phase) {
        PHASE_REST -> "do_not_touch_the_phone"
        PHASE_CARRY -> "pick_the_phone_up_deliberately_and_carry_it_naturally"
        else -> "put_the_phone_back_down_and_leave_it_still"
    }

    private fun phaseOf(offsetMs: Long, phases: List<Pair<String, Long>>): String {
        var remaining = offsetMs
        for ((name, durationMs) in phases) {
            if (remaining < durationMs) return name
            remaining -= durationMs
        }
        return phases.last().first
    }

    /** One continuous stream, started and stopped inside the adapter that owns registration (A-12). */
    private fun measure(windowMs: Long): Stream {
        val collected = Collections.synchronizedList(mutableListOf<Arrived>())
        val adapter = MotionSensorAdapter(sensorManager)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val result = adapter.startReporting({ sample ->
                collected += Arrived(sample, System.currentTimeMillis())
            }, REQUESTED_REPORT_PERIOD_US)
            val beganAt = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - beganAt < windowMs) {
                Thread.sleep(100)
            }
            // The sensor thread appends to the same list, and a synchronized list still iterates
            // unsafely unless the copy itself holds the lock — measured on RMX3910, where reading the
            // window while events were still arriving threw ConcurrentModificationException.
            adapter.stopReporting()
            val arrived = synchronized(collected) { collected.toList() }
            return Stream(
                startResult = result,
                samples = arrived.map { it.sample },
                arrived = arrived,
                deliveredSampleCount = adapter.deliveredSampleCount,
                clockResolutionDrops = adapter.clockResolutionDrops,
            )
        } finally {
            adapter.stopReporting()
            scenario.close()
        }
    }

    private fun summarize(phase: String, signal: MotionSignal, clockResolutionDrops: Int): PhaseSummary {
        val deviations = signal.points.map { it.gravityDeltaMs2 }.sorted()
        return PhaseSummary(
            phase = phase,
            inputCount = signal.inputSampleCount,
            deviationMin = deviations.minOrNull() ?: Double.NaN,
            deviationMedian = percentile(deviations, 50.0),
            deviationP90 = percentile(deviations, 90.0),
            deviationP99 = percentile(deviations, 99.0),
            deviationMax = deviations.maxOrNull() ?: Double.NaN,
            medianIntervalMs = signal.medianIntervalMs,
            longestGapMs = signal.longestIntervalMs,
            clockResolutionDrops = clockResolutionDrops,
            sensorQuality = signal.quality,
            droppedSamples = signal.droppedSampleCount,
            points = signal.points,
        )
    }

    private fun reportRaw(phase: String, points: List<MotionSignalPoint>) {
        points.chunked(RAW_CHUNK).forEachIndexed { index, chunk ->
            Log.i(TAG, "RAW phase=$phase chunk=$index count=${chunk.size} " +
                chunk.joinToString(separator = " | ") {
                    "elapsedMs=${it.elapsedMs} x=${it.x} y=${it.y} z=${it.z} " +
                        "magnitude=${it.magnitudeMs2} gravity_deviation=${it.gravityDeltaMs2}"
                })
        }
    }

    private fun reportDerived(summary: PhaseSummary) {
        Log.i(TAG, "DERIVED phase=${summary.phase} sample_count=${summary.inputCount} " +
            "dropped_samples=${summary.droppedSamples} deviation_min=${summary.deviationMin} " +
            "deviation_median=${summary.deviationMedian} deviation_p90=${summary.deviationP90} " +
            "deviation_p99=${summary.deviationP99} deviation_max=${summary.deviationMax} " +
            "median_interval_ms=${summary.medianIntervalMs} longest_gap_ms=${summary.longestGapMs} " +
            "clock_resolution_drops=${summary.clockResolutionDrops} sensor_quality=${summary.sensorQuality}")
    }

    /**
     * Longest stretch of readings that stayed above [level], measured in sensor time. Two readings
     * further apart than [SAMPLING_CONTINUITY_MS] do not make one stretch, so a stalled stream can
     * never look like a long movement.
     */
    private fun longestRunAboveMs(points: List<MotionSignalPoint>, level: Double): Long {
        var best = 0L
        var start: Long? = null
        var previous: Long? = null
        for (point in points) {
            val gap = previous?.let { point.elapsedMs - it } ?: 0L
            if (point.gravityDeltaMs2 > level && (start == null || gap <= SAMPLING_CONTINUITY_MS)) {
                if (start == null) start = point.elapsedMs
                best = maxOf(best, point.elapsedMs - start)
            } else {
                start = null
            }
            previous = point.elapsedMs
        }
        return best
    }

    private fun percentile(sorted: List<Double>, percentile: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private class Stream(
        val startResult: MotionStartResult,
        val samples: List<MotionSample>,
        val arrived: List<Arrived>,
        val deliveredSampleCount: Int,
        val clockResolutionDrops: Int,
    )

    private class Arrived(val sample: MotionSample, val arrivedAtMs: Long)

    private class PhaseSummary(
        val phase: String,
        val inputCount: Int,
        val deviationMin: Double,
        val deviationMedian: Double,
        val deviationP90: Double,
        val deviationP99: Double,
        val deviationMax: Double,
        val medianIntervalMs: Double?,
        val longestGapMs: Long?,
        val clockResolutionDrops: Int,
        val sensorQuality: MotionSignalQuality,
        val droppedSamples: Int,
        val points: List<MotionSignalPoint>,
    )

    private companion object {
        const val TAG = "MotionCalibration"

        /** A request, never a claim about the delivered rate (A-12). */
        const val REQUESTED_REPORT_PERIOD_US = 20_000

        /** The signal layer's own stall rule: a multiplier on the measured median, not milliseconds. */
        const val STALL_TOLERANCE_FACTOR = 3.0

        const val REST_ONLY_WINDOW_MS = 12_000L
        const val PHASE_REST = "rest"
        const val PHASE_CARRY = "carry"
        const val PHASE_SETTLE = "settle"
        const val PHASE_REST_MS = 8_000L
        const val PHASE_CARRY_MS = 20_000L
        const val PHASE_SETTLE_MS = 6_000L

        /** How many readings share one `RAW` line. */
        const val RAW_CHUNK = 20

        /** A stretch shorter than this is a hand tremor, not the §5 carry. */
        const val MIN_CARRY_RUN_MS = 1_500L

        /** Readings further apart than this do not form one continuous stretch. */
        const val SAMPLING_CONTINUITY_MS = 1_000L

        // PROBE: `multiplier` and `levels` are searchlights. The multiplier is applied to the rest
        // maximum of the same run, and the levels are reported so the owner can see how much time was
        // spent above each one. Neither is a candidate product threshold: Q-G1 is the owner's answer.
        const val PROBE_REST_MULTIPLIER = 3.0
        val PROBE_LEVELS_MS2 = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0)
    }
}
