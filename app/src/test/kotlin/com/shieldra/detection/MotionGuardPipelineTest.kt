package com.shieldra.detection

import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.Clock
import com.shieldra.domain.DefaultProtectionStateEngine
import com.shieldra.domain.EventId
import com.shieldra.domain.GuardConfirmationStatus
import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardObservation
import com.shieldra.domain.GuardSignal
import com.shieldra.domain.IdentityProvider
import com.shieldra.domain.SensorQuality
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batch G / slice G5: the one composition the approved numbers are used through.
 *
 * The three pure layers G1-G4 already exist — describe the stream, judge the windows, bridge to the
 * engine — and until G5 nothing joined them in the order the owner approved. The pipeline is that
 * join, and these tests pin it to the A-17/A-18 calibration: the episode definition and the
 * confirmation policy stay separate rules, and both of the stream limits (the 11× factor and the
 * absolute 100 ms ceiling) refuse rather than downgrade.
 */
class MotionGuardPipelineTest {
    private val pipeline = MotionGuardPipeline(
        calibration = ApprovedMotionCalibration.motionCalibration,
        stallToleranceFactor = ApprovedMotionCalibration.stallToleranceFactor,
    )

    private val observedAt = Instant.parse("2026-10-01T13:00:00Z")
    private val clock = Clock { observedAt }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }
    private val engine = DefaultProtectionStateEngine()

    @Test
    fun a_clean_three_second_movement_is_valid_and_confirms_under_the_owner_s_rule() {
        val observation = pipeline.observe(strong(count = 160))

        assertEquals(SensorQuality.VALID, observation.sensorQuality)
        assertTrue(
            observation.duration >= ApprovedGuardRules.motionStrongDuration,
            "160 samples 20 ms apart span more than three seconds: ${observation.duration}",
        )
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(observation).confirmation.status)
    }

    @Test
    fun an_episode_at_the_approved_minimum_is_an_episode_and_is_not_a_confirmation() {
        // A-17's binding separation, seen from the other side: 500 ms is what makes a movement an
        // episode at all; it is not a shortcut past the ~3 s (or two-inside-10 s) confirmation rule.
        val observation = pipeline.observe(strong(count = 31))

        assertEquals(SensorQuality.VALID, observation.sensorQuality)
        assertEquals(Duration.ofMillis(600L), observation.duration)
        assertEquals(1, observation.suspiciousOccurrences)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun a_movement_shorter_than_the_approved_episode_is_dropped_before_any_rule_runs() {
        val observation = pipeline.observe(strong(count = 11))

        assertEquals(Duration.ZERO, observation.duration)
        assertEquals(0, observation.suspiciousOccurrences)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun the_absolute_gap_ceiling_refuses_a_stream_the_median_factor_alone_would_have_allowed() {
        // The owner's rule for a rising median: a 150 ms stop is inside 11x the measured 20 ms median,
        // and the absolute 100 ms ceiling still governs, so nothing is claimed from that stream.
        val observation = pipeline.observe(strong(count = 100, gapAfterIndex = 49, gapMs = 130L))

        assertEquals(SensorQuality.NOISY, observation.sensorQuality)
        assertEquals(Duration.ZERO, observation.duration, "an untrusted stream reports no movement at all")
        assertEquals(0, observation.suspiciousOccurrences)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun a_stop_inside_both_approved_limits_still_delivers_one_valid_movement() {
        val observation = pipeline.observe(strong(count = 200, gapAfterIndex = 49, gapMs = 60L))

        assertEquals(SensorQuality.VALID, observation.sensorQuality, "an 80 ms stop is inside 11x the median and inside 100 ms")
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(observation).confirmation.status)
    }

    @Test
    fun two_movements_inside_the_owner_s_ten_second_window_confirm_by_repetition() {
        val observation = pipeline.observe(
            strong(count = 31) + quiet(count = 100, afterMs = 620L) + strong(count = 31, afterMs = 2620L),
        )

        assertEquals(SensorQuality.VALID, observation.sensorQuality)
        assertEquals(2, observation.suspiciousOccurrences)
        assertTrue(
            observation.duration < ApprovedGuardRules.motionStrongDuration,
            "neither episode alone reaches three seconds, so only repetition can confirm: ${observation.duration}",
        )
        val within = observation.suspiciousOccurrencesWithin
        assertTrue(
            within != null && within <= ApprovedGuardRules.motionRepetitionWindow,
            "the two movements must sit inside the owner's ten-second window: $within",
        )
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(observation).confirmation.status)
    }

    @Test
    fun nothing_measured_is_reported_as_unavailable_rather_than_as_a_still_device() {
        val observation = pipeline.observe(emptyList())

        assertEquals(SensorQuality.UNAVAILABLE, observation.sensorQuality)
        assertEquals(Duration.ZERO, observation.duration)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun a_value_that_is_not_an_acceleration_is_never_reported_as_valid() {
        val samples = strong(count = 160).toMutableList().also { list ->
            list[80] = MotionSample(elapsedMs = list[80].elapsedMs, x = Double.NaN, y = 0.0, z = 9.80665)
        }

        val observation = pipeline.observe(samples)

        assertEquals(SensorQuality.NOISY, observation.sensorQuality)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    private fun confirm(observation: GuardObservation.Motion) =
        engine.evaluate(GuardSignal(GuardKind.MOTION, observedAt, observation), clock, identities)

    /** [count] readings 20 ms apart, all above the approved strong level; one may carry a [gapMs] stop. */
    private fun strong(count: Int, gapAfterIndex: Int? = null, gapMs: Long = 0L, afterMs: Long = 0L): List<MotionSample> =
        (0 until count).map { index ->
            MotionSample(
                elapsedMs = afterMs + index * 20L + if (gapAfterIndex != null && index > gapAfterIndex) gapMs else 0L,
                x = 0.0,
                y = 0.0,
                z = 9.80665 + 5.0,
            )
        }

    /** Readings below the approved noise floor, which is what closes an episode. */
    private fun quiet(count: Int, afterMs: Long): List<MotionSample> =
        (0 until count).map { MotionSample(elapsedMs = afterMs + it * 20L, x = 0.0, y = 0.0, z = 9.80665 + 0.5) }
}
