package com.shieldra.protection

import com.shieldra.detection.MotionCalibration
import com.shieldra.detection.MotionSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A-47: the challenge is for a snatch, not for movement. These cases are the owner's own examples,
 * stated without a device: walking, running for the bus, lifting the phone and putting it back,
 * riding one's own motorcycle — ordinary; a grab from rest that becomes running or a vehicle —
 * a snatch. The calibration here is test-local on purpose; stage 3 measures the shipping numbers on
 * the owner's phone.
 */
class SnatchSignaturePolicyTest {

    private val motion = MotionCalibration(
        strongDeltaMs2 = 4.6,
        noiseFloorDeltaMs2 = 0.8,
        minEpisodeMs = 500L,
        maxSampleGapMs = 100L,
    )

    private val signature = SnatchSignatureCalibration(
        grabPeakDeltaMs2 = 12.0,
        calmBeforeGrabMs = 2_000L,
        grabMaxDurationMs = 800L,
        followMinPeakDeltaMs2 = 6.0,
        followWindowMs = 5_000L,
        followMinMovementMs = 1_200L,
        followMaxGapMs = 500L,
    )

    @Test
    fun `walking with the phone in hand stays ordinary`() {
        val samples = stream(
            Segment(500, 0.2),
            Segment(400, 2.0),
            Segment(300, 0.6),
            Segment(400, 3.0),
            Segment(300, 0.5),
            Segment(400, 2.4),
            Segment(400, 1.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `running for the bus with the phone already moving stays ordinary`() {
        // The first hard footfall is above the grab level, but the window starts with it: no calm
        // was measured before it, so it cannot be the grab this rule asks about.
        val samples = stream(
            Segment(300, 13.0),
            Segment(200, 5.0),
            Segment(300, 13.5),
            Segment(2_000, 8.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `lifting the phone and putting it down stays ordinary`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(500, 16.0),
            Segment(3_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `riding the owner's motorcycle with the phone already mounted stays ordinary`() {
        val samples = stream(
            Segment(500, 5.0),
            Segment(4_000, 7.0),
            Segment(500, 4.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `a grab from rest followed by running is a snatch`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(2_000, 8.0),
            Segment(500, 0.2),
        )

        val verdict = SnatchSignaturePolicy.classify(samples, motion, signature)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(3_000L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `a grab from rest followed by a vehicle is a snatch`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(3_000, 9.0),
        )

        val verdict = SnatchSignaturePolicy.classify(samples, motion, signature)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
    }

    @Test
    fun `a grab from rest followed by stillness stays ordinary`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 18.0),
            Segment(3_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `a grab without a measured calm before it is not a grab`() {
        // The phone was already being handled when the sharp reading arrived; that is a hand in
        // motion, not a phone lifted off a surface.
        val samples = stream(
            Segment(3_000, 2.0),
            Segment(400, 16.0),
            Segment(3_000, 8.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `a stalled stream is ordinary even when its shape looks like a snatch`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(2_000, 8.0),
            stepMs = 200L,
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `a non-finite reading is ordinary rather than an accusation`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(2_000, 8.0),
        ).toMutableList()
        samples[10] = samples[10].copy(y = Double.NaN)

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, SnatchSignaturePolicy.classify(samples, motion, signature))
    }

    @Test
    fun `the onset reported is the first grab-level reading after the calm`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(200, 16.0),
            Segment(200, 14.0),
            Segment(2_000, 8.0),
        )

        val verdict = SnatchSignaturePolicy.classify(samples, motion, signature)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(3_000L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `a follow-up window that cannot hold the required movement is refused`() {
        assertFailsWith<IllegalArgumentException> {
            signature.copy(followMinMovementMs = 5_000L)
        }
    }

    private data class Segment(val durationMs: Long, val deviation: Double)

    /**
     * A readable stream at a constant deviation per segment. The x axis carries gravity plus the
     * deviation, so the Motion layer's own orientation-free maths reads exactly the value asked for.
     */
    private fun stream(vararg segments: Segment, stepMs: Long = 20L): List<MotionSample> {
        var at = 0L
        val samples = mutableListOf<MotionSample>()
        for (segment in segments) {
            var elapsed = 0L
            while (elapsed < segment.durationMs) {
                samples += MotionSample(
                    elapsedMs = at,
                    x = 9.80665 + segment.deviation,
                    y = 0.0,
                    z = 0.0,
                )
                at += stepMs
                elapsed += stepMs
            }
        }
        return samples
    }
}
