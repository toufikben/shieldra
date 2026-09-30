package com.shieldra.detection

import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.Clock
import com.shieldra.domain.DefaultProtectionStateEngine
import com.shieldra.domain.GuardConfirmationStatus
import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardObservation
import com.shieldra.domain.GuardSignal
import com.shieldra.domain.IdentityProvider
import com.shieldra.domain.SensorQuality
import com.shieldra.domain.EventId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batch G / G1-02: turning raw accelerometer samples into the approved Motion observation.
 *
 * The engine already encodes the owner's *time* rule (3 s strong, or two suspicious movements
 * inside 10 s, only with valid sensor quality). What this file pins down is the half that does
 * not exist yet: measuring how long a movement really lasted, throwing away isolated noise, and
 * refusing to claim anything when the sensor stopped talking.
 *
 * Every magnitude number here is an injected test fixture. The shipping threshold is Q-G1 and is
 * the owner's to set from measured samples, so nothing in this file may treat 4.0 or 1.2 as
 * product policy.
 */
class MotionWindowEvaluatorTest {

    private val calibration = MotionCalibration(
        strongDeltaMs2 = 4.0,
        noiseFloorDeltaMs2 = 1.2,
        minEpisodeMs = 400,
        maxSampleGapMs = 500,
    )
    private val evaluator = MotionWindowEvaluator(calibration)

    private val observedAt = Instant.parse("2026-09-30T22:00:00Z")
    private val clock = Clock { observedAt }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }
    private val engine = DefaultProtectionStateEngine()

    /** Flat phone held still, then moved: the whole deviation sits on the gravity axis. */
    private fun flat(delta: Double, count: Int, startMs: Long = 0, stepMs: Long = 20): List<MotionSample> =
        (0 until count).map { MotionSample(elapsedMs = startMs + it * stepMs, x = 0.0, y = 0.0, z = 9.80665 + delta) }

    private fun reading(reading: MotionReading): GuardObservation.Motion =
        MotionGuardObservation.of(reading)

    private fun confirm(observation: GuardObservation.Motion) =
        engine.evaluate(GuardSignal(GuardKind.MOTION, observedAt, observation), clock, identities)

    @Test
    fun a_strong_movement_that_last_longer_than_the_approved_three_seconds_confirms_a_security_event() {
        // 200 samples at 20 ms = 4 s held above the strong level.
        val movement = evaluator.read(flat(delta = 5.0, count = 200))
        val episodes = (movement as MotionReading.Movements).episodes
        assertEquals(1, episodes.size, "one continuous movement is one episode, was: $episodes")
        assertTrue(episodes.single().durationMs >= 3_000, "4 s of movement must measure at least 3 s, was ${episodes.single().durationMs} ms")

        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(reading(movement)).confirmation.status)
    }

    @Test
    fun a_burst_shorter_than_the_minimum_episode_is_isolated_noise_and_never_an_occurrence() {
        val movement = evaluator.read(flat(delta = 5.0, count = 10)) // 200 ms, below minEpisodeMs
        assertEquals(MotionReading.Movements(emptyList()), movement, "a 200 ms spike is noise, not a movement")
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(reading(movement)).confirmation.status)
    }

    @Test
    fun two_strong_bursts_inside_the_repetition_window_confirm_and_a_single_one_does_not() {
        val pair = evaluator.read(
            flat(delta = 5.0, count = 40) +          // 0.8 s
                flat(delta = 0.0, count = 60, startMs = 800, stepMs = 20) +
                flat(delta = 5.0, count = 40, startMs = 2000), // second burst, 2 s later
        )
        assertEquals(2, (pair as MotionReading.Movements).episodes.size)
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(reading(pair)).confirmation.status)

        val single = evaluator.read(flat(delta = 5.0, count = 40))
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(reading(single)).confirmation.status)
    }

    @Test
    fun two_bursts_eleven_seconds_apart_are_outside_the_approved_window_and_stay_signal_only() {
        // The quiet stretch between the bursts is real sampled quiet, not a hole: 510 samples at
        // 20 ms cover 800 ms..10 980 ms, so the eleven-second separation is the owner's rule being
        // exercised rather than an accidental sampling gap tripping the unreliable path.
        val readingValue = evaluator.read(
            flat(delta = 5.0, count = 40) +
                flat(delta = 0.0, count = 510, startMs = 800) +
                flat(delta = 5.0, count = 40, startMs = 11_000),
        )
        assertEquals(2, (readingValue as MotionReading.Movements).episodes.size)
        val observation = MotionGuardObservation.of(readingValue)
        assertEquals(
            1,
            observation.suspiciousOccurrences,
            "only the episodes inside the trailing ${ApprovedGuardRules.motionRepetitionWindow.toMillis()} ms window count",
        )
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(observation).confirmation.status)
    }

    @Test
    fun a_silence_longer_than_the_sampling_limit_is_unreliable_rather_than_a_quiet_device() {
        val withGap = flat(delta = 0.5, count = 5) + flat(delta = 0.5, count = 5, startMs = 5_000)
        assertTrue(evaluator.read(withGap) is MotionReading.Unreliable, "a 5 s hole means the sensor stopped talking, not that nothing moved")
    }

    @Test
    fun a_non_finite_sample_is_unreliable_and_can_never_confirm() {
        val broken = flat(delta = 5.0, count = 100) +
            MotionSample(elapsedMs = 2_000, x = Double.NaN, y = 0.0, z = 9.80665)
        val readingValue = evaluator.read(broken)
        assertTrue(readingValue is MotionReading.Unreliable)
        assertEquals(SensorQuality.NOISY, MotionGuardObservation.of(readingValue).sensorQuality)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(MotionGuardObservation.of(readingValue)).confirmation.status)
    }

    @Test
    fun no_samples_are_insufficient_and_are_never_reported_as_a_still_device() {
        assertEquals(MotionReading.Insufficient, evaluator.read(emptyList()))
        assertEquals(SensorQuality.UNAVAILABLE, MotionGuardObservation.of(MotionReading.Insufficient).sensorQuality)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, confirm(MotionGuardObservation.of(MotionReading.Insufficient)).confirmation.status)
    }

    @Test
    fun the_same_movement_on_a_phone_turned_on_its_side_measures_the_same() {
        val flatPhone = flat(delta = 5.0, count = 200)
        val sidePhone = flatPhone.map { MotionSample(it.elapsedMs, it.z, it.y, it.x) }
        assertEquals(
            evaluator.read(flatPhone),
            evaluator.read(sidePhone),
            "magnitude deviation is orientation-free; a turned phone must not change the verdict",
        )
    }

    @Test
    fun values_between_the_noise_floor_and_the_strong_level_start_no_episode() {
        val restless = flat(delta = 2.0, count = 400) // above the floor, below strong, for 8 s
        assertEquals(MotionReading.Movements(emptyList()), evaluator.read(restless))
    }

    @Test
    fun a_movement_that_never_settles_reports_the_full_measured_duration_not_zero() {
        val movement = evaluator.read(flat(delta = 6.0, count = 160)) // 3.2 s, ends mid-burst
        val episode = (movement as MotionReading.Movements).episodes.single()
        assertEquals(3_180L, episode.durationMs, "the burst ends at the last sample, so the measured span is honest")
        assertEquals(ApprovedGuardRules.motionStrongDuration.toMillis(), 3_000L)
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirm(reading(movement)).confirmation.status)
    }

    @Test
    fun the_peak_recorded_for_an_episode_is_the_largest_deviation_it_saw() {
        val rising = (0 until 100).map { MotionSample(it * 20L, 0.0, 0.0, 9.80665 + it * 0.1) }
        val movement = evaluator.read(rising)
        val episode = (movement as MotionReading.Movements).episodes.single()
        assertEquals(9.9, episode.peakDeltaMs2, 1e-6, "the peak is what a later severity or report needs, and it must be real")
    }
}
