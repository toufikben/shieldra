package com.shieldra.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Batch G / slice G3: the sampling-and-quality half of the approved adapter scope.
 *
 * Owner ruling A-12 fixes what this layer may and may not do. It may turn raw accelerometer
 * samples into signal data — magnitude, the *measured* interval between readings, and a quality
 * verdict computed from the data itself. It may not decide that anything happened: no Security
 * Event, no severity, no threshold that means "attack". That is why the stall tolerance is an
 * injected factor and every magnitude here is a fixture.
 *
 * Nothing in this file asserts a device number as a contract. The rate is always derived from the
 * timestamps that actually arrived, because RMX3910 delivered ~106 Hz when 50 Hz was requested —
 * a measurement, not a spec.
 */
class MotionSignalNormalizerTest {

    private val normalizer = MotionSignalNormalizer(stallToleranceFactor = 3.0)

    private fun at(elapsedMs: Long, magnitudeOnZ: Double = 9.80665) =
        MotionSample(elapsedMs = elapsedMs, x = 0.0, y = 0.0, z = magnitudeOnZ)

    @Test
    fun every_usable_sample_becomes_a_point_with_its_magnitude_and_its_deviation_from_gravity() {
        val signal = normalizer.normalize(
            listOf(at(0L), at(10L, magnitudeOnZ = 12.0), at(20L, magnitudeOnZ = 9.80665)),
        )

        assertEquals(3, signal.points.size, "every finite, in-order sample must survive")
        assertEquals(0, signal.droppedSampleCount, "nothing was dropped, so nothing may be reported as dropped")
        assertTrue(
            kotlin.math.abs(signal.points[1].magnitudeMs2 - 12.0) < 1e-9,
            "the magnitude must be the length of the acceleration vector",
        )
        assertTrue(
            kotlin.math.abs(signal.points[1].gravityDeltaMs2 - (12.0 - 9.80665)) < 1e-9,
            "the deviation must be measured against rest gravity, not against zero",
        )
        assertEquals(MotionSignalQuality.USABLE, signal.quality)
    }

    @Test
    fun the_deviation_is_orientation_free_so_the_same_reading_on_any_axis_measures_the_same() {
        val flat = normalizer.normalize(listOf(at(0L, 9.80665), at(10L, 14.0)))
        val onItsSide = normalizer.normalize(
            listOf(
                MotionSample(0L, 9.80665, 0.0, 0.0),
                MotionSample(10L, 0.0, 14.0, 0.0),
            ),
        )

        assertEquals(
            flat.points[1].gravityDeltaMs2,
            onItsSide.points[1].gravityDeltaMs2,
            1e-9,
            "a phone held upright must measure the same movement as one lying flat",
        )
    }

    @Test
    fun fewer_than_two_samples_is_reported_as_nothing_measured_not_as_a_still_device() {
        val one = normalizer.normalize(listOf(at(0L)))
        val none = normalizer.normalize(emptyList())

        assertEquals(MotionSignalQuality.EMPTY, one.quality, "one sample measures no interval at all")
        assertEquals(MotionSignalQuality.EMPTY, none.quality)
        assertEquals(null, one.medianIntervalMs, "one sample is not a measured interval")
        assertEquals(null, one.measuredRateHz, "a rate that was never measured must not be invented")
        assertEquals(null, none.measuredRateHz)
        for (signal in listOf(one, none)) {
            assertTrue(
                "${signal.qualityReasons} must say nothing was measured, which is not the same as a still device",
                signal.qualityReasons.any { it.contains("Nothing was measured", ignoreCase = true) },
            )
        }
    }

    @Test
    fun a_sample_that_is_not_a_finite_acceleration_is_dropped_and_the_stream_is_marked_invalid() {
        val signal = normalizer.normalize(
            listOf(at(0L), at(10L, Double.NaN), at(20L), at(30L, Double.POSITIVE_INFINITY)),
        )

        assertEquals(2, signal.points.size, "the two readable samples must still reach the next layer")
        assertEquals(2, signal.droppedSampleCount)
        assertEquals(MotionSignalQuality.INVALID, signal.quality)
        assertTrue(
            signal.qualityReasons.joinToString().contains("not finite", ignoreCase = true),
            "the reason must name what the sensor actually did",
        )
    }

    @Test
    fun two_samples_that_share_one_millisecond_are_invalid_because_the_clock_cannot_separate_them() {
        val signal = normalizer.normalize(listOf(at(0L), at(5L), at(5L), at(20L)))

        assertEquals(MotionSignalQuality.INVALID, signal.quality)
        assertTrue(
            signal.qualityReasons.joinToString().contains("millisecond", ignoreCase = true),
            "a colliding timestamp must be reported as a clock-resolution problem, not as stillness",
        )
    }

    @Test
    fun a_sample_that_arrives_before_the_previous_one_is_invalid() {
        val signal = normalizer.normalize(listOf(at(0L), at(10L), at(4L)))

        assertEquals(MotionSignalQuality.INVALID, signal.quality)
        assertTrue(
            signal.qualityReasons.joinToString().contains("order", ignoreCase = true),
            "out-of-order time is a timing fault the next layer must not have to guess",
        )
    }

    @Test
    fun the_sampling_rate_is_measured_from_the_timestamps_that_arrived_not_from_what_was_requested() {
        val samples = (0L until 5L).map { at(it * 10L) }

        val signal = normalizer.normalize(samples)

        assertEquals(10.0, signal.medianIntervalMs!!, 1e-9, "the interval must come from the timestamps")
        assertEquals(100.0, signal.measuredRateHz!!, 1e-9, "the rate is derived from the measured interval")
        assertEquals(10L, signal.longestIntervalMs)
    }

    @Test
    fun a_delivery_that_stops_beyond_the_injected_tolerance_is_irregular_and_reports_the_measured_numbers() {
        val signal = normalizer.normalize(
            listOf(at(0L), at(10L), at(20L), at(320L), at(330L)),
        )

        assertEquals(MotionSignalQuality.IRREGULAR, signal.quality)
        assertEquals(300L, signal.longestIntervalMs, "the stalled interval must be the one reported")
        assertTrue(
            signal.qualityReasons.joinToString().contains("300"),
            "the reason must quote the number that was measured, not a constant from this file",
        )
    }

    @Test
    fun jitter_that_stays_inside_the_tolerance_is_usable_because_a_suggestion_is_not_a_promise() {
        val signal = normalizer.normalize(
            listOf(at(0L), at(9L), at(21L), at(30L), at(39L)),
        )

        assertEquals(MotionSignalQuality.USABLE, signal.quality)
        assertEquals(12L, signal.longestIntervalMs)
    }

    @Test
    fun a_signal_with_no_usable_points_never_carries_a_measured_rate() {
        val signal = normalizer.normalize(listOf(at(0L, Double.NaN), at(10L, Double.NaN)))

        assertEquals(0, signal.points.size)
        assertEquals(null, signal.medianIntervalMs)
        assertEquals(null, signal.measuredRateHz)
        assertEquals(MotionSignalQuality.INVALID, signal.quality)
    }

    @Test
    fun the_stall_tolerance_has_to_be_a_real_number_above_one() {
        for (bad in listOf(0.0, 1.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try {
                MotionSignalNormalizer(stallToleranceFactor = bad)
                fail("a tolerance of $bad would call ordinary frame jitter a stall")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.contains("tolerance"), "message was: ${expected.message}")
            }
        }
    }
}
