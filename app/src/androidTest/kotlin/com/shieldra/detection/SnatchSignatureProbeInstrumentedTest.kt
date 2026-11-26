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
import com.shieldra.protection.SnatchSignatureCalibration
import com.shieldra.protection.SnatchSignaturePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import kotlin.math.ceil

/**
 * Batch G / A-47 stage 3: the measurements the owner's snatch-signature decision has to stand on,
 * taken on this phone, through the same adapter (A-12) and the same deviation maths the product
 * uses. Nothing here is a product level.
 *
 * The owner's rule (A-47, 2026-11-10) is that a sharp grab from a measured calm followed by
 * sustained strong locomotion is what opens the challenge — and that a carried phone, a lift, a
 * run that started from the hand and a motorcycle ride must NOT. Those four ordinary cases are
 * measured here too, so the numbers separate the shapes instead of being argued about.
 *
 * What this file is allowed to do is narrow on purpose. It samples through [MotionSensorAdapter],
 * describes each reading with [MotionSignalNormalizer], reports every reading and every derived
 * figure, and classifies the SAME window against a grid of candidate signatures that are labelled
 * `PROBE`. It creates no Security Event, no alert, no notification, no stored row and no file; it
 * touches no camera, no location, no channel and no keyguard. It never moves the phone: the pickup,
 * the carry, the run and the shake are done by the device owner with their hands.
 *
 * `RAW` lines are what the sensor delivered. `DERIVED` lines are summaries computed afterwards.
 * `PROBE_*` lines are searchlights: `PROBE_SIGNATURE` says which candidate would have called this
 * window a snatch, and `OWNERSHIP` records that no shipping signature exists yet. A probe is never
 * a candidate threshold — the owner answers the signature from the reported ranges.
 */
@RunWith(AndroidJUnit4::class)
class SnatchSignatureProbeInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * Ordinary case 1: a phone left still is picked up, looked at, and put back down. The owner
     * does this by hand; the window is a single measurement so no phase clock has to be trusted.
     */
    @Test
    fun ordinary_lift_and_put_down_is_probed_and_must_not_look_like_a_snatch() {
        val windowMs = 22_000L
        Log.i(TAG, "INSTRUCTION suite=snatch case=ordinary_lift window_ms=$windowMs " +
            "sequence=leave_it_still_for_5s | pick_it_up_look_at_the_screen_hold_it_4s | " +
            "put_it_back_down | leave_it_still_until_the_window_ends")
        val stream = measure(windowMs)
        assertEquals(
            "the probe measures through the verified adapter, so a refused registration is reported " +
                "rather than measured around: ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )
        logEnvironment("ordinary_lift", windowMs, stream)
        val signal = MotionSignalNormalizer(PROBE_STALL_TOLERANCE_FACTOR).normalize(stream.samples)
        reportRaw(PHASE_WINDOW, signal.points)
        reportDerived(summarize(PHASE_WINDOW, signal, stream.clockResolutionDrops))
        probe(signal)
        assertUsable(stream, signal)
    }

    /**
     * Ordinary case 2: the phone is already in the hand when the window starts and the owner walks
     * normally. This is the "run for the bus with the phone in hand" shape: no calm, no grab.
     */
    @Test
    fun ordinary_carried_walk_is_probed_and_must_not_look_like_a_snatch() {
        val windowMs = 20_000L
        Log.i(TAG, "INSTRUCTION suite=snatch case=ordinary_walk window_ms=$windowMs " +
            "sequence=hold_the_phone_in_your_hand_before_sampling_starts | " +
            "walk_normally_with_it_for_the_whole_window | do_not_put_it_down")
        val stream = measure(windowMs)
        assertEquals(
            "the probe measures through the verified adapter, was ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )
        logEnvironment("ordinary_walk", windowMs, stream)
        val signal = MotionSignalNormalizer(PROBE_STALL_TOLERANCE_FACTOR).normalize(stream.samples)
        reportRaw(PHASE_WINDOW, signal.points)
        reportDerived(summarize(PHASE_WINDOW, signal, stream.clockResolutionDrops))
        probe(signal)
        assertUsable(stream, signal)
    }

    /**
     * The shape the owner described: still on a table, a deliberate grab, then running with it.
     * The owner performs the sequence; the window is measured as one stream.
     */
    @Test
    fun grab_then_run_is_probed_after_a_measured_calm() {
        val windowMs = 30_000L
        Log.i(TAG, "INSTRUCTION suite=snatch case=grab_then_run window_ms=$windowMs " +
            "sequence=leave_it_still_on_a_table_for_6s | grab_it_deliberately_and_start_running " +
            "run_in_place_or_along_the_room_for_15s | put_it_back_down_and_leave_it_still_until_the_window_ends")
        val stream = measure(windowMs)
        assertEquals(
            "the probe measures through the verified adapter, was ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )
        logEnvironment("grab_then_run", windowMs, stream)
        val signal = MotionSignalNormalizer(PROBE_STALL_TOLERANCE_FACTOR).normalize(stream.samples)
        reportRaw(PHASE_WINDOW, signal.points)
        reportDerived(summarize(PHASE_WINDOW, signal, stream.clockResolutionDrops))
        probe(signal)
        assertUsable(stream, signal)
    }

    /**
     * The second shape the owner described: a grab from rest that continues into strong, sustained
     * vibration as if the phone were mounted on a moving motorcycle. The shake is done by hand.
     */
    @Test
    fun grab_then_simulated_vehicle_vibration_is_probed() {
        val windowMs = 30_000L
        Log.i(TAG, "INSTRUCTION suite=snatch case=grab_then_vehicle window_ms=$windowMs " +
            "sequence=leave_it_still_on_a_table_for_6s | grab_it_deliberately | " +
            "hold_it_and_shake_it_steadily_as_if_mounted_on_a_moving_motorcycle_for_18s | " +
            "put_it_back_down_until_the_window_ends")
        val stream = measure(windowMs)
        assertEquals(
            "the probe measures through the verified adapter, was ${stream.startResult}",
            MotionStartResult.Started,
            stream.startResult,
        )
        logEnvironment("grab_then_vehicle", windowMs, stream)
        val signal = MotionSignalNormalizer(PROBE_STALL_TOLERANCE_FACTOR).normalize(stream.samples)
        reportRaw(PHASE_WINDOW, signal.points)
        reportDerived(summarize(PHASE_WINDOW, signal, stream.clockResolutionDrops))
        probe(signal)
        assertUsable(stream, signal)
    }

    private fun assertUsable(stream: Stream, signal: MotionSignal) {
        assertTrue(
            "a window must be measured, not assumed: ${stream.samples.size} samples",
            stream.samples.size >= 2,
        )
        assertTrue(
            "every reported reading must be a finite acceleration, quality was ${signal.quality}",
            signal.points.all { it.gravityDeltaMs2.isFinite() },
        )
    }

    /** One continuous stream, started and stopped inside the adapter that owns registration (A-12). */
    private fun measure(windowMs: Long): Stream {
        val collected = Collections.synchronizedList(mutableListOf<MotionSample>())
        val adapter = MotionSensorAdapter(sensorManager)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val result = adapter.startReporting(
                { sample -> collected += sample },
                ApprovedMotionCalibration.requestedReportPeriodUs,
            )
            val beganAt = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - beganAt < windowMs) {
                Thread.sleep(100)
            }
            adapter.stopReporting()
            val samples = synchronized(collected) { collected.toList() }
            return Stream(result, samples, adapter.deliveredSampleCount, adapter.clockResolutionDrops)
        } finally {
            adapter.stopReporting()
            scenario.close()
        }
    }

    private fun logEnvironment(kind: String, windowMs: Long, stream: Stream) {
        Log.i(TAG, "ENVIRONMENT kind=$kind device=${Build.MODEL} product=${Build.DEVICE} " +
            "android_release=${Build.VERSION.RELEASE} android_sdk=${Build.VERSION.SDK_INT} " +
            "requested_report_period_us=${ApprovedMotionCalibration.requestedReportPeriodUs} " +
            "window_ms=$windowMs screen_interactive_at_end=${powerManager.isInteractive} " +
            "adapter_delivered=${stream.deliveredSampleCount} clock_resolution_drops=${stream.clockResolutionDrops}")
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
     * The searchlight half. It reports the calm that preceded the peak, how much movement followed
     * it at each probe level, and which candidate signatures would have called this window a
     * snatch. Every candidate is logged with its own numbers so the owner can see the separation
     * rather than accept one engineer's pick.
     */
    private fun probe(signal: MotionSignal) {
        val points = signal.points
        val maxGap = points.zipWithNext().maxOfOrNull { (earlier, later) -> later.elapsedMs - earlier.elapsedMs } ?: -1L
        val gapsOverLimit = points.zipWithNext().count { (earlier, later) ->
            later.elapsedMs - earlier.elapsedMs > ApprovedMotionCalibration.maxSampleGapMs
        }
        Log.i(TAG, "PROBE_STREAM samples=${points.size} max_gap_ms=$maxGap " +
            "gaps_over_limit=$gapsOverLimit classifier_gap_limit_ms=${ApprovedMotionCalibration.maxSampleGapMs}")

        val peakIndex = points.indices.maxByOrNull { points[it].gravityDeltaMs2 }
        if (peakIndex == null) {
            Log.i(TAG, "PROBE_PEAK absent=the_window_delivered_no_readings")
            return
        }
        val peak = points[peakIndex]
        var calmMs = 0L
        var lastAt = peak.elapsedMs
        var index = peakIndex - 1
        while (index >= 0) {
            val gap = lastAt - points[index].elapsedMs
            if (gap > ApprovedMotionCalibration.maxSampleGapMs) break
            if (points[index].gravityDeltaMs2 >= ApprovedMotionCalibration.noiseFloorDeltaMs2) break
            calmMs += gap
            lastAt = points[index].elapsedMs
            index--
        }
        Log.i(TAG, "PROBE_PEAK at_ms=${peak.elapsedMs} deviation=${peak.gravityDeltaMs2} " +
            "calm_before_ms=$calmMs noise_floor=${ApprovedMotionCalibration.noiseFloorDeltaMs2} " +
            "min_episode_ms=${ApprovedMotionCalibration.minEpisodeMs} strong_level=${ApprovedMotionCalibration.strongDeltaMs2}")

        val afterPeak = points.drop(peakIndex)
        PROBE_LEVELS_MS2.forEach { level ->
            Log.i(TAG, "PROBE_LEVEL level=$level above_after_peak_count=${afterPeak.count { it.gravityDeltaMs2 > level }} " +
                "longest_run_after_peak_ms=${longestRunAboveMs(afterPeak, level)} " +
                "movement_after_peak_ms=${movementAboveMs(afterPeak, level, PROBE_FOLLOW_GAP_MS)}")
        }

        val samples = points.map { MotionSample(it.elapsedMs, it.x, it.y, it.z) }
        PROBE_SIGNATURES.forEach { (label, signature) ->
            val verdict = SnatchSignaturePolicy.classify(
                samples,
                ApprovedMotionCalibration.motionCalibration,
                signature,
            )
            val call = when (verdict) {
                is SnatchSignaturePolicy.Verdict.Snatch -> "snatch grab_at_ms=${verdict.grabAtMs}"
                is SnatchSignaturePolicy.Verdict.Ordinary -> "ordinary"
            }
            Log.i(TAG, "PROBE_SIGNATURE label=$label verdict=$call " +
                "grab_peak_ms2=${signature.grabPeakDeltaMs2} calm_before_ms=${signature.calmBeforeGrabMs} " +
                "grab_max_ms=${signature.grabMaxDurationMs} follow_peak_ms2=${signature.followMinPeakDeltaMs2} " +
                "follow_window_ms=${signature.followWindowMs} follow_min_ms=${signature.followMinMovementMs} " +
                "follow_gap_ms=${signature.followMaxGapMs} purpose=searchlight NOT_a_product_threshold")
        }
        Log.i(TAG, "OWNERSHIP a47_signature=UNANSWERED shipped_snatch_calibration=false measured_only=TRUE")
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

    /** The policy's own movement sum, reported here so the owner sees the figure the classifier uses. */
    private fun movementAboveMs(points: List<MotionSignalPoint>, level: Double, maxGapMs: Long): Long {
        var moved = 0L
        var previousQualifyingAt: Long? = null
        for (point in points) {
            if (point.gravityDeltaMs2 < level) continue
            val previous = previousQualifyingAt
            if (previous != null) {
                val gap = point.elapsedMs - previous
                if (gap <= maxGapMs) moved += gap
            }
            previousQualifyingAt = point.elapsedMs
        }
        return moved
    }

    private fun percentile(sorted: List<Double>, percentile: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val rank = ceil(percentile / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private class Stream(
        val startResult: MotionStartResult,
        val samples: List<MotionSample>,
        val deliveredSampleCount: Int,
        val clockResolutionDrops: Int,
    )

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

        const val PHASE_WINDOW = "window"

        /** The same probe tolerance the Q-G1 probe used; quality reporting only, never a decision. */
        const val PROBE_STALL_TOLERANCE_FACTOR = 3.0

        /** How many readings share one `RAW` line. */
        const val RAW_CHUNK = 20

        /** Readings further apart than this do not form one continuous stretch. */
        const val SAMPLING_CONTINUITY_MS = 1_000L

        /** The policy's own follow-up gap limit, used by the probe grid below. */
        const val PROBE_FOLLOW_GAP_MS = 500L

        val PROBE_LEVELS_MS2 = listOf(0.8, 1.5, 2.0, 3.0, 4.6, 6.0, 8.0, 10.0, 12.0)

        // PROBE grid: every candidate is a searchlight, not a shipping value. The owner's answer is
        // the only thing that may become ApprovedSnatchSignatureCalibration after this measurement.
        val PROBE_SIGNATURES: List<Pair<String, SnatchSignatureCalibration>> = listOf(
            "peak3_follow2" to SnatchSignatureCalibration(3.0, 2_500, 1_000, 2.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
            "peak5_follow2" to SnatchSignatureCalibration(5.0, 2_500, 1_000, 2.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
            "peak7_follow2" to SnatchSignatureCalibration(7.0, 2_500, 1_000, 2.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
            "peak7_follow3" to SnatchSignatureCalibration(7.0, 2_500, 1_000, 3.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
            "peak10_follow3" to SnatchSignatureCalibration(10.0, 2_500, 1_000, 3.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
            "peak12_follow6" to SnatchSignatureCalibration(12.0, 2_500, 1_000, 6.0, 6_000, 1_500, PROBE_FOLLOW_GAP_MS),
        )
    }
}
