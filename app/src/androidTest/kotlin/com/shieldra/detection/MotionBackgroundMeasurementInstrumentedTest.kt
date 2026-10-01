package com.shieldra.detection

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Batch G / slice G4-A: does the accelerometer keep delivering to *this process* while the phone
 * lies still and its screen is dark? Amendment A-13 makes that the only question this file may ask,
 * and it is a measurement, not a verification: no phase asserts that delivery continued, because a
 * failing assertion would be an architectural conclusion written in the wrong place.
 *
 * Four phases, one `am instrument` invocation each, three repetitions per phase on RMX3910. Phase A
 * is the baseline with the screen on; B, C and D are 60 s, 5 min and 15 min with the screen off.
 * Each screen-off window is preceded by a settle wait that proves the screen really went to sleep
 * before the measurement clock starts, and the screen state is polled throughout the window, so a
 * run whose screen stayed lit is reported as contaminated instead of being counted as an answer.
 *
 * The whole window is waited out on a worker thread. A JUnit4 test body runs on the instrumentation
 * main thread, and blocking that thread for fifteen minutes can get the process stopped for reasons
 * that have nothing to do with the sensor path; a stopped process would then be measured as
 * interrupted delivery and read as an answer about foreground services. The worker keeps the
 * measurement honest about the question it is actually asking.
 *
 * What this cannot show, and A-13 says so in advance: instrumentation keeps the process alive and
 * gives it test-process priority, so these numbers describe an instrumented process, not a
 * production background service under Doze, App Standby or OEM restrictions. Continued delivery is
 * not proof that a foreground service is unnecessary; interrupted delivery is not proof that
 * `specialUse` is required.
 *
 * delivery_state rule (A-13, test-only, quoted in the static gate): NO_DATA when fewer than two
 * samples arrived inside the window; CONTINUING when the longest interval and the gap from the last
 * sample to the window end both stayed within STALL_TOLERANCE_FACTOR times the median interval;
 * INTERRUPTED otherwise, or when the run raised. The factor is a window parameter, not a detection
 * threshold, and no number measured here may become a shipping constant.
 */
@RunWith(AndroidJUnit4::class)
class MotionBackgroundMeasurementInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Test
    fun phase_a_baseline_with_the_screen_on_measures_the_reference_rate() {
        runPhase(Phase(name = "A_screen_on_baseline", windowMs = PHASE_A_WINDOW_MS, expectsScreenOff = false))
    }

    @Test
    fun phase_b_screen_off_sixty_seconds_measures_whether_delivery_survives_the_screen_going_dark() {
        runPhase(Phase(name = "B_screen_off_60s", windowMs = PHASE_B_WINDOW_MS, expectsScreenOff = true))
    }

    @Test
    fun phase_c_screen_off_five_minutes_measures_whether_delivery_survives_a_longer_interval() {
        runPhase(Phase(name = "C_screen_off_5min", windowMs = PHASE_C_WINDOW_MS, expectsScreenOff = true))
    }

    @Test
    fun phase_d_screen_off_fifteen_minutes_looks_for_degradation_or_cessation_over_a_long_interval() {
        runPhase(Phase(name = "D_screen_off_15min", windowMs = PHASE_D_WINDOW_MS, expectsScreenOff = true))
    }

    private data class Phase(val name: String, val windowMs: Long, val expectsScreenOff: Boolean)

    /** One phase's raw observation, kept in one place so no evidence field is passed positionally. */
    private data class Observation(
        val phase: Phase,
        val runIndex: String,
        val requestedPeriodUs: Int,
        val registration: String,
        val settleWaitedMs: Long,
        val screenOffBeforeWindow: Boolean,
        val polls: Int,
        val interactivePolls: Int,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val startWallClockMs: Long,
        val endWallClockMs: Long,
        val actualElapsedMs: Long,
        val samples: List<MotionSample>,
        val deliveredSampleCount: Int,
        val clockResolutionDrops: Int,
        val reportingAfterStop: Boolean,
        val exception: String?,
    )

    private fun runPhase(phase: Phase) {
        val args = InstrumentationRegistry.getArguments()
        val windowMs = args.longArg("window_ms") ?: phase.windowMs
        val settleDeadlineMs = args.longArg("settle_deadline_ms") ?: DEFAULT_SETTLE_DEADLINE_MS
        val raised = arrayOfNulls<String>(1)
        val worker = Thread({ raised[0] = observe(phase, args) }, "shieldra-g4a-observer")

        worker.start()
        val guardMs = windowMs + settleDeadlineMs + JOIN_MARGIN_MS
        worker.join(guardMs)
        if (worker.isAlive) {
            throw AssertionError(
                "G4-A phase ${phase.name} was still measuring after $guardMs ms, so the runner gave up on it",
            )
        }
        raised[0]?.let { message -> throw AssertionError("G4-A phase ${phase.name} raised: $message") }
    }

    /** Measures one phase, writes its evidence line, and returns the exception text if it raised. */
    private fun observe(phase: Phase, args: Bundle): String? {
        val windowMs = args.longArg("window_ms") ?: phase.windowMs
        val requestedPeriodUs = args.intArg("requested_period_us") ?: DEFAULT_REQUESTED_PERIOD_US
        val settleDeadlineMs = args.longArg("settle_deadline_ms") ?: DEFAULT_SETTLE_DEADLINE_MS
        val pollMs = args.longArg("poll_ms") ?: DEFAULT_POLL_MS
        val runIndex = args.getString("run_index") ?: "1"

        val delivered = Delivered()
        val adapter = MotionSensorAdapter(sensorManager)
        var registration = NOT_ATTEMPTED
        var settleWaitedMs = 0L
        var screenOffBeforeWindow = false
        var windowStartMs = 0L
        var windowEndMs = 0L
        var startWallClockMs = 0L
        var endWallClockMs = 0L
        var actualElapsedMs = 0L
        var polls = 0
        var interactivePolls = 0
        var raised: String? = null

        try {
            // Registered before the screen goes dark: the question is whether an established
            // subscription keeps feeding the process, not whether registration works in the dark.
            registration = adapter.startReporting(delivered::offer, requestedPeriodUs).toString()

            if (phase.expectsScreenOff) {
                val settleStart = SystemClock.elapsedRealtime()
                val deadline = settleStart + settleDeadlineMs
                while (powerManager.isInteractive && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(SETTLE_POLL_MS)
                }
                screenOffBeforeWindow = !powerManager.isInteractive
                settleWaitedMs = SystemClock.elapsedRealtime() - settleStart
            }

            windowStartMs = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MILLI
            startWallClockMs = System.currentTimeMillis()
            var remaining = windowMs
            while (remaining > 0) {
                val slice = minOf(pollMs, remaining)
                Thread.sleep(slice)
                remaining -= slice
                polls++
                if (powerManager.isInteractive) interactivePolls++
            }
            endWallClockMs = System.currentTimeMillis()
            windowEndMs = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MILLI
            actualElapsedMs = endWallClockMs - startWallClockMs
        } catch (thrown: Throwable) {
            raised = "${thrown.javaClass.name}: ${thrown.message}"
        }

        // Unregister, let an event already dispatched on the collector thread land, then take the
        // one snapshot the evidence is built from. The list is a synchronized wrapper, and iterating
        // it while the delivery thread still holds it is documented as unsafe, so the copy is locked.
        stopQuietly(adapter)
        Thread.sleep(SETTLE_AFTER_STOP_MS)
        val samples = synchronized(delivered.samples) { delivered.samples.toList() }

        emit(
            Observation(
                phase = phase.copy(windowMs = windowMs),
                runIndex = runIndex,
                requestedPeriodUs = requestedPeriodUs,
                registration = registration,
                settleWaitedMs = settleWaitedMs,
                screenOffBeforeWindow = screenOffBeforeWindow,
                polls = polls,
                interactivePolls = interactivePolls,
                windowStartMs = windowStartMs,
                windowEndMs = windowEndMs,
                startWallClockMs = startWallClockMs,
                endWallClockMs = endWallClockMs,
                actualElapsedMs = actualElapsedMs,
                samples = samples,
                deliveredSampleCount = adapter.deliveredSampleCount,
                clockResolutionDrops = adapter.clockResolutionDrops,
                reportingAfterStop = adapter.isReporting,
                exception = raised,
            ),
        )
        return raised
    }

    private fun stopQuietly(adapter: MotionSensorAdapter) {
        try {
            adapter.stopReporting()
        } catch (thrown: Throwable) {
            // The evidence matters more than a teardown failure, and the failure is itself evidence
            // that the stream outlived its owner, so it is reported rather than swallowed.
            Log.w(TAG, "G4-A stopReporting raised: ${thrown.javaClass.name}: ${thrown.message}")
        }
    }

    private fun emit(observation: Observation) {
        val windowStartMs = observation.windowStartMs
        val windowEndMs = observation.windowEndMs
        val windowSamples = observation.samples.filter { it.elapsedMs in windowStartMs..windowEndMs }
        val intervals = windowSamples.zipWithNext { earlier, later -> later.elapsedMs - earlier.elapsedMs }
        val signal = MotionSignalNormalizer(STALL_TOLERANCE_FACTOR).normalize(windowSamples)
        val median = signal.medianIntervalMs
        val toleranceMs = median?.let { it * STALL_TOLERANCE_FACTOR }
        val tailGapMs = windowSamples.lastOrNull()?.let { windowEndMs - it.elapsedMs }
        val oversized = toleranceMs?.let { limit -> intervals.filter { it > limit } }.orEmpty()
        val windowDurationMs = windowEndMs - windowStartMs

        val deliveryState = when {
            observation.exception != null -> INTERRUPTED
            windowSamples.size < 2 || median == null || median <= 0.0 || toleranceMs == null -> NO_DATA
            oversized.isNotEmpty() || (tailGapMs ?: 0L) > toleranceMs -> INTERRUPTED
            else -> CONTINUING
        }
        val contamination = when {
            observation.exception != null -> "exception_raised_during_the_run"
            observation.phase.expectsScreenOff && !observation.screenOffBeforeWindow ->
                "screen_never_went_asleep_within_the_settle_deadline"
            observation.phase.expectsScreenOff && observation.interactivePolls > 0 ->
                "screen_was_interactive_in_${observation.interactivePolls}_of_${observation.polls}_polls"
            observation.polls > 0 && windowDurationMs < observation.phase.windowMs / 2 ->
                "the_window_measured_${windowDurationMs}_ms_instead_of_${observation.phase.windowMs}_ms"
            else -> null
        }

        val fields = listOf(
            "schema" to RUN_SCHEMA,
            "phase" to observation.phase.name,
            "run_index" to observation.runIndex,
            "device_model" to Build.MODEL,
            "device_manufacturer" to Build.MANUFACTURER,
            "device_board" to Build.BOARD,
            "android_version" to Build.VERSION.RELEASE,
            "android_sdk" to Build.VERSION.SDK_INT,
            "display_build" to Build.DISPLAY,
            "requested_period_us" to observation.requestedPeriodUs,
            "requested_duration_ms" to observation.phase.windowMs,
            "actual_elapsed_ms" to observation.actualElapsedMs,
            "settle_waited_ms" to observation.settleWaitedMs,
            "screen_off_confirmed_before_window" to observation.screenOffBeforeWindow,
            "screen_interactive_polls_during_window" to observation.interactivePolls,
            "polls_during_window" to observation.polls,
            "window_start_elapsed_ms" to windowStartMs,
            "window_end_elapsed_ms" to windowEndMs,
            "start_wall_clock_ms" to observation.startWallClockMs,
            "end_wall_clock_ms" to observation.endWallClockMs,
            "registration_result" to observation.registration,
            "total_samples_collected" to observation.samples.size,
            "window_sample_count" to windowSamples.size,
            "samples_before_window" to observation.samples.size - windowSamples.size,
            "adapter_delivered_count" to observation.deliveredSampleCount,
            "clockResolutionDrops" to observation.clockResolutionDrops,
            "first_sample_elapsed_ms" to windowSamples.firstOrNull()?.elapsedMs,
            "last_sample_elapsed_ms" to windowSamples.lastOrNull()?.elapsedMs,
            "median_interval_ms" to median,
            "min_interval_ms" to intervals.minOrNull(),
            "max_interval_ms" to signal.longestIntervalMs,
            "derived_rate_hz" to signal.measuredRateHz,
            "tail_gap_ms" to tailGapMs,
            "tolerance_factor" to STALL_TOLERANCE_FACTOR,
            "oversized_gap_count" to oversized.size,
            "first_oversized_gap_ms" to oversized.firstOrNull(),
            "signal_quality" to signal.quality.name,
            "dropped_sample_count" to signal.droppedSampleCount,
            "stopped_before_window_end" to (toleranceMs != null && (tailGapMs ?: 0L) > toleranceMs),
            "reporting_after_stop" to observation.reportingAfterStop,
            "exception" to observation.exception,
            "delivery_state" to deliveryState,
            "contaminated" to (contamination != null),
            "contamination_reason" to contamination,
        )
        Log.i(TAG, "G4A_JSON=" + buildString { json(fields) })
    }

    /** Delivery sink. The samples live behind a synchronized list because the sensor callback runs
     * on the adapter's own thread while the observation is being assembled. */
    private class Delivered {
        val samples: MutableList<MotionSample> = Collections.synchronizedList(mutableListOf<MotionSample>())

        fun offer(sample: MotionSample) {
            samples.add(sample)
        }
    }

    /** A test-only JSON writer: this harness must emit evidence without a serialization dependency
     * and without letting an exception message break the line. */
    private fun StringBuilder.json(fields: List<Pair<String, Any?>>) {
        append('{')
        fields.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            quoted(key)
            append(':')
            jsonValue(value)
        }
        append('}')
    }

    private fun StringBuilder.jsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> quoted(value)
            is Boolean, is Int, is Long -> append(value.toString())
            is Double -> append(if (value.isFinite()) value.toString() else "null")
            else -> quoted(value.toString())
        }
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u").append(String.format("%04x", character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private fun Bundle.longArg(name: String): Long? =
        getString(name)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    private fun Bundle.intArg(name: String): Int? =
        getString(name)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    private companion object {
        const val TAG = "ShieldraG4A"
        const val RUN_SCHEMA = "shieldra-g4a-run-v1"
        const val NOT_ATTEMPTED = "not_attempted"
        const val NO_DATA = "NO_DATA"
        const val CONTINUING = "CONTINUING"
        const val INTERRUPTED = "INTERRUPTED"
        const val STALL_TOLERANCE_FACTOR = 3.0
        const val PHASE_A_WINDOW_MS = 60_000L
        const val PHASE_B_WINDOW_MS = 60_000L
        const val PHASE_C_WINDOW_MS = 300_000L
        const val PHASE_D_WINDOW_MS = 900_000L
        const val DEFAULT_REQUESTED_PERIOD_US = 20_000
        const val DEFAULT_SETTLE_DEADLINE_MS = 120_000L
        const val DEFAULT_POLL_MS = 5_000L
        const val SETTLE_POLL_MS = 250L
        const val SETTLE_AFTER_STOP_MS = 500L
        const val JOIN_MARGIN_MS = 180_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
