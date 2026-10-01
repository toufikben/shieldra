package com.shieldra.detection

import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.Clock
import com.shieldra.domain.DefaultProtectionStateEngine
import com.shieldra.domain.GuardConfirmationStatus
import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardObservation
import com.shieldra.domain.GuardSignal
import com.shieldra.domain.IdentityProvider
import com.shieldra.domain.EventId
import com.shieldra.domain.SensorQuality
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch G / slice G4: the validation half that was missing between signal data and the approved rule.
 *
 * Slice G3 measures whether a stream can be believed ([MotionSignalNormalizer], from delivered
 * timestamps and the values themselves). Slice G1 measures how long a movement lasted
 * ([MotionWindowEvaluator], from the samples it was handed). Until now nothing joined the two: the
 * bridge looked only at the evaluator's verdict, so four seconds of accelerometer data whose stream
 * had stalled could still enter the engine as `SensorQuality.VALID` and confirm a Security Event.
 *
 * These tests pin the join, and they pin it failing closed: stream quality is an input the caller
 * must state, it can only ever *lower* what the observation claims, and a stream that cannot be
 * believed reports no movement at all — not a movement with a warning attached.
 *
 * The magnitude numbers here are test fixtures. Q-G1 (the shipping strong-motion level) is still
 * unanswered, so no number in this file is product policy and none of them may be read as one.
 */
class MotionStreamQualityTest {

    private val fixture = MotionCalibration(
        strongDeltaMs2 = 4.0,
        noiseFloorDeltaMs2 = 1.2,
        minEpisodeMs = 400,
        maxSampleGapMs = 500,
    )
    private val evaluator = MotionWindowEvaluator(fixture)

    private val observedAt = Instant.parse("2026-10-01T09:00:00Z")
    private val clock = Clock { observedAt }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }
    private val engine = DefaultProtectionStateEngine()

    /** Flat phone, so the whole deviation sits on the gravity axis and is orientation-free anyway. */
    private fun flat(delta: Double, count: Int, startMs: Long = 0, stepMs: Long = 20): List<MotionSample> =
        (0 until count).map { MotionSample(elapsedMs = startMs + it * stepMs, x = 0.0, y = 0.0, z = 9.80665 + delta) }

    /** Four seconds of movement above the fixture's strong level: enough to confirm on a trusted stream. */
    private fun strongMovement(): MotionReading = evaluator.read(flat(delta = 5.0, count = 200))

    private fun confirm(observation: GuardObservation.Motion) =
        engine.evaluate(GuardSignal(GuardKind.MOTION, observedAt, observation), clock, identities)

    @Test
    fun a_stream_the_signal_layer_measured_as_irregular_is_never_reported_with_valid_sensor_quality() {
        val movement = strongMovement()
        assertTrue(
            "the fixture movement must hold episodes, otherwise this test proves nothing: $movement",
            (movement as MotionReading.Movements).episodes.isNotEmpty(),
        )
        assertEquals(
            SensorQuality.NOISY,
            MotionGuardObservation.of(movement, MotionSignalQuality.IRREGULAR).sensorQuality,
            "a stalled stream cannot be upgraded to valid just because the samples it did deliver looked strong",
        )
    }

    @Test
    fun a_stream_the_signal_layer_measured_as_invalid_is_never_reported_with_valid_sensor_quality() {
        assertEquals(
            SensorQuality.NOISY,
            MotionGuardObservation.of(strongMovement(), MotionSignalQuality.INVALID).sensorQuality,
            "a stream carrying a value that is not an acceleration is refused whatever the movement looked like",
        )
    }

    @Test
    fun a_stream_with_nothing_measured_is_reported_as_unavailable_rather_than_as_a_still_device() {
        assertEquals(
            SensorQuality.UNAVAILABLE,
            MotionGuardObservation.of(strongMovement(), MotionSignalQuality.EMPTY).sensorQuality,
            "EMPTY means nobody measured, which is not the same as the phone sitting still",
        )
    }

    @Test
    fun an_untrusted_stream_reports_no_movement_at_all_rather_than_a_movement_it_cannot_believe() {
        val observation = MotionGuardObservation.of(strongMovement(), MotionSignalQuality.IRREGULAR)
        assertEquals(Duration.ZERO, observation.duration, "a refused stream may not contribute a measured duration")
        assertEquals(0, observation.suspiciousOccurrences, "a refused stream may not contribute occurrences")
        assertNull(observation.suspiciousOccurrencesWithin, "a refused stream may not contribute a repetition window")
    }

    @Test
    fun a_repeated_movement_on_an_untrusted_stream_never_reaches_the_owner_s_repetition_rule() {
        val pair = evaluator.read(
            flat(delta = 5.0, count = 40) +
                flat(delta = 0.0, count = 60, startMs = 800) +
                flat(delta = 5.0, count = 40, startMs = 2_000),
        )
        assertEquals(2, (pair as MotionReading.Movements).episodes.size, "the fixture must really produce two episodes")
        val observation = MotionGuardObservation.of(pair, MotionSignalQuality.INVALID)
        assertEquals(0, observation.suspiciousOccurrences)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun three_seconds_of_strong_movement_on_an_untrusted_stream_stays_signal_only() {
        assertEquals(
            GuardConfirmationStatus.SIGNAL_ONLY,
            confirm(MotionGuardObservation.of(strongMovement(), MotionSignalQuality.IRREGULAR)).confirmation.status,
            "the approved time rule may only run on sensor data the stream actually supported",
        )
    }

    @Test
    fun the_same_movement_on_a_trusted_stream_still_confirms() {
        // The refusal must not turn into "Motion never confirms": that would hide the rule working
        // behind a permanent no, and the owner's rule would be unverifiable.
        val observation = MotionGuardObservation.of(strongMovement(), MotionSignalQuality.USABLE)
        assertEquals(SensorQuality.VALID, observation.sensorQuality)
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(observation).confirmation.status)
        assertTrue(
            "a trusted stream must report the measured length, was ${observation.duration}",
            observation.duration >= ApprovedGuardRules.motionStrongDuration,
        )
    }

    @Test
    fun an_untrusted_stream_is_refused_for_every_injected_calibration() {
        val weak = MotionCalibration(strongDeltaMs2 = 1.5, noiseFloorDeltaMs2 = 0.4, minEpisodeMs = 400, maxSampleGapMs = 500)
        for (calibration in listOf(fixture, weak)) {
            val movement = MotionWindowEvaluator(calibration).read(flat(delta = 5.0, count = 200))
            assertEquals(
                GuardConfirmationStatus.SIGNAL_ONLY,
                confirm(MotionGuardObservation.of(movement, MotionSignalQuality.IRREGULAR)).confirmation.status,
                "calibration $calibration must not buy a confirmation with an untrusted stream",
            )
        }
    }

    @Test
    fun changing_the_injected_calibration_changes_the_decision_deterministically() {
        val movement = evaluator.read(flat(delta = 2.0, count = 200))
        assertEquals(
            GuardConfirmationStatus.SIGNAL_ONLY,
            confirm(MotionGuardObservation.of(movement, MotionSignalQuality.USABLE)).confirmation.status,
            "2.0 is below the fixture's strong level, so the same samples must not confirm",
        )
        val lower = MotionWindowEvaluator(
            MotionCalibration(strongDeltaMs2 = 1.5, noiseFloorDeltaMs2 = 0.4, minEpisodeMs = 400, maxSampleGapMs = 500),
        ).read(flat(delta = 2.0, count = 200))
        assertEquals(
            GuardConfirmationStatus.CONFIRMED,
            confirm(MotionGuardObservation.of(lower, MotionSignalQuality.USABLE)).confirmation.status,
            "the same samples must confirm once the injected level is lower: the decision is the calibration's, not the code's",
        )
    }

    @Test
    fun a_deviation_below_the_noise_floor_starts_no_episode() {
        assertEquals(MotionReading.Movements(emptyList()), evaluator.read(flat(delta = 0.5, count = 200)))
    }

    @Test
    fun a_deviation_just_under_the_noise_floor_starts_no_episode() {
        assertEquals(MotionReading.Movements(emptyList()), evaluator.read(flat(delta = 1.19, count = 200)))
    }

    @Test
    fun a_deviation_between_the_noise_floor_and_the_strong_level_starts_no_episode() {
        assertEquals(MotionReading.Movements(emptyList()), evaluator.read(flat(delta = 2.0, count = 200)))
    }

    @Test
    fun a_deviation_just_under_the_strong_level_starts_no_episode() {
        assertEquals(MotionReading.Movements(emptyList()), evaluator.read(flat(delta = 3.9, count = 200)))
    }

    @Test
    fun a_deviation_above_the_strong_level_starts_an_episode() {
        val movement = evaluator.read(flat(delta = 4.1, count = 200)) as MotionReading.Movements
        assertEquals(1, movement.episodes.size)
        assertTrue(
            "the episode must span the movement, was ${movement.episodes.single().durationMs} ms",
            movement.episodes.single().durationMs >= 3_000,
        )
    }
}
