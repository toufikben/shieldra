package com.shieldra.detection

import com.shieldra.domain.ApprovedGuardRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batch G / slice G5: the shipping Motion calibration, pinned to the owner's numbers.
 *
 * Amendments A-17 and A-18 together approve exactly six numbers. Nothing here is a measurement
 * turned into a constant by an engineer: a change to any of the six must fail CI at this file, and
 * a seventh number cannot enter without a new amendment in `docs/authority`.
 */
class ApprovedMotionCalibrationTest {

    @Test
    fun the_shipping_motion_calibration_is_exactly_the_owner_s_six_numbers() {
        assertEquals(4.6, ApprovedMotionCalibration.strongDeltaMs2, A18)
        assertEquals(0.8, ApprovedMotionCalibration.noiseFloorDeltaMs2, A18)
        assertEquals(500L, ApprovedMotionCalibration.minEpisodeMs, A18)
        assertEquals(100L, ApprovedMotionCalibration.maxSampleGapMs, A18)
        assertEquals(20_000, ApprovedMotionCalibration.requestedReportPeriodUs, A18)
        assertEquals(11.0, ApprovedMotionCalibration.stallToleranceFactor, A18)
    }

    @Test
    fun the_assembled_calibration_holds_the_four_window_numbers_and_no_fifth_source_exists() {
        val calibration = ApprovedMotionCalibration.motionCalibration

        assertEquals(4.6, calibration.strongDeltaMs2, A18)
        assertEquals(0.8, calibration.noiseFloorDeltaMs2, A18)
        assertEquals(500L, calibration.minEpisodeMs, A18)
        assertEquals(100L, calibration.maxSampleGapMs, A18)
        // The reporting period and the stall tolerance are not part of MotionCalibration; they are
        // the two numbers that belong to the stream rather than to a window.
        assertEquals(
            MotionCalibration(
                strongDeltaMs2 = 4.6,
                noiseFloorDeltaMs2 = 0.8,
                minEpisodeMs = 500,
                maxSampleGapMs = 100,
            ),
            calibration,
            A18,
        )
    }

    @Test
    fun the_stall_tolerance_is_a_factor_the_normalizer_accepts_and_sits_under_the_absolute_gap_ceiling() {
        // A-18: 11.0 was derived, not chosen — at the measured ~9 ms median it asks for ~99 ms, just
        // inside maxSampleGapMs. If the median rises on a device, the 100 ms ceiling governs, so the
        // factor must never be able to permit a stall the ceiling forbids.
        val median = 9.0
        assertTrue(
            median * ApprovedMotionCalibration.stallToleranceFactor <= ApprovedMotionCalibration.maxSampleGapMs,
            "the approved factor must not widen the stall tolerance past the absolute gap ceiling at the measured median",
        )
        val normalizer = MotionSignalNormalizer(ApprovedMotionCalibration.stallToleranceFactor)
        assertEquals(MotionSignalQuality.USABLE, normalizer.normalize(tenSamples(stallMs = 84)).quality)
        assertEquals(MotionSignalQuality.IRREGULAR, normalizer.normalize(tenSamples(stallMs = 111)).quality)
    }

    @Test
    fun the_episode_definition_never_replaces_the_owner_s_confirmation_policy() {
        // A-17 separates them by ruling: minEpisodeMs says when a movement is an episode at all;
        // G1-02's ~3 s (or two inside 10 s) says when a movement is a security event. The shorter
        // number may not be used to satisfy the longer rule.
        assertTrue(
            ApprovedMotionCalibration.minEpisodeMs < ApprovedGuardRules.motionStrongDuration.toMillis(),
            "an episode definition shorter than the confirmation policy is the point of the separation",
        )
        assertEquals(3_000L, ApprovedGuardRules.motionStrongDuration.toMillis(), A18)
        assertEquals(10_000L, ApprovedGuardRules.motionRepetitionWindow.toMillis(), A18)
        assertEquals(2, ApprovedGuardRules.motionOccurrencesToConfirm, A18)
    }

    /** Ten readings 10 ms apart with one gap of [stallMs] inside them. */
    private fun tenSamples(stallMs: Long): List<MotionSample> {
        val times = listOf(0L, 10L, 20L, 30L, 40L, 50L, 50L + stallMs, 60L + stallMs, 70L + stallMs, 80L + stallMs)
        return times.map { MotionSample(elapsedMs = it, x = 0.0, y = 0.0, z = 9.80665) }
    }

    private companion object {
        const val A18 = "amendment A-18 owns this number; changing it here is a policy edit by an engineer"
    }
}
