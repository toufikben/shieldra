package com.shieldra.protection

import com.shieldra.detection.ApprovedMotionCalibration
import com.shieldra.detection.MotionSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A-47: the challenge is for a snatch, not for movement. The ordinary and snatch shapes below are
 * the RAW windows measured on the owner's phone on 2026-11-11 (A-47/3), kept at the measured shape
 * and scaled to test length: a calm, a burst that carries a sharp reading, then the follow-up.
 * [ApprovedSnatchSignatureCalibration.signature] goes through the policy unchanged, so these tests
 * pin the shipping numbers rather than a test-local guess.
 *
 * Measured, at exactly this follow-up definition: ordinary lifts and walks reached 0 ms of movement
 * at 14 m/s²; ordinary pocket walking reached 548, 236 and 804 ms; a grab that became running
 * reached 4772 and 4488 ms, and one that became a vehicle 2272 and 1911 ms. The 1200 ms requirement
 * separates the two families.
 */
class SnatchSignaturePolicyTest {

    private val motion = ApprovedMotionCalibration.motionCalibration

    private val signature = ApprovedSnatchSignatureCalibration.signature

    @Test
    fun `the shipping signature is pinned to the stage three measurement`() {
        // Seven measurements from the owner's phone, not preferences. Changing one without a new
        // measurement set is exactly what this test exists to catch.
        assertEquals(3.0, signature.grabPeakDeltaMs2)
        assertEquals(2_500L, signature.calmBeforeGrabMs)
        assertEquals(1_000L, signature.grabMaxDurationMs)
        assertEquals(14.0, signature.followMinPeakDeltaMs2)
        assertEquals(6_000L, signature.followWindowMs)
        assertEquals(1_200L, signature.followMinMovementMs)
        assertEquals(500L, signature.followMaxGapMs)
    }

    @Test
    fun `walking with the phone in hand stays ordinary`() {
        // A real walk from rest: the burst starts after a measured calm and carries sharp footfalls,
        // but every reading stays below 14 m/s², so the follow-up is empty.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 2.0),
            Segment(300, 0.6),
            Segment(400, 3.0),
            Segment(300, 0.6),
            Segment(400, 2.4),
            Segment(300, 0.6),
            Segment(400, 3.0),
            Segment(300, 2.0),
            Segment(2_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `running for the bus with the phone already moving stays ordinary`() {
        // The window opens while the phone is already in motion: there is no measured calm before
        // the burst's first reading, so it cannot be the grab this rule asks about.
        val samples = stream(
            Segment(300, 13.0),
            Segment(200, 5.0),
            Segment(300, 13.5),
            Segment(2_000, 8.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `lifting the phone and putting it down stays ordinary`() {
        // The measured ordinary window: a sharp lift, then quiet. The follow-up window holds no
        // movement at 14 m/s² at all.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(3_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `pocket walking reaches the follow level briefly and stays ordinary`() {
        // The measured worst ordinary window reached 804 ms; this shape spends 820 ms at 15 m/s²
        // (two 300 ms stretches bridged by a 220 ms gap), still 380 ms short of the requirement.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(1_000, 8.0),
            Segment(320, 15.0),
            Segment(200, 9.0),
            Segment(320, 15.0),
            Segment(3_000, 8.0),
            Segment(2_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `an unplug spike followed by walking stays ordinary`() {
        // The first burst is a motionless spike, measured on every pocket window as the cable was
        // released. Its follow-up is stillness, so it is no snatch; the walking that starts later
        // has its own calm and stays below the follow level.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(40, 20.0),
            Segment(3_000, 0.2),
            Segment(3_000, 8.0),
            Segment(2_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `the grab after a motionless spike is still tried once the spike's follow-up is over`() {
        // The same cable spike with nothing strong inside its own 6 s follow-up, then a real grab
        // from rest more than 6 s later. A classifier that spends its one question on the first
        // burst answers ordinary; the owner's rule is about the grab that did happen.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(40, 20.0),
            Segment(6_500, 0.2),
            Segment(200, 2.0),
            Segment(200, 16.0),
            Segment(2_000, 15.0),
            Segment(3_000, 0.2),
        )

        val verdict = classify(samples)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(9_540L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `riding the owner's motorcycle from rest stays ordinary`() {
        // The owner's own example: the phone is mounted and the ride begins. Engine vibration is
        // strong enough to confirm Motion, but the measured ride never holds 14 m/s², so the
        // follow-up is empty.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(500, 4.0),
            Segment(4_000, 7.0),
            Segment(500, 4.0),
            Segment(2_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `a grab from rest followed by running is a snatch`() {
        // The measured grab-run shape: the burst ramps out of the noise band to 16 m/s² and then
        // holds 15 m/s² through the follow-up window (2280 ms here, like the measured 4772/4488).
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(200, 2.0),
            Segment(200, 16.0),
            Segment(400, 10.0),
            Segment(2_500, 15.0),
            Segment(3_000, 0.2),
        )

        val verdict = classify(samples)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(3_000L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `the weakest measured grab followed by a vehicle is still a snatch`() {
        // Vehicle window 3 was the weakest snatch shape at 1911 ms; this stream holds 15 m/s² for
        // 1900 ms inside the follow-up window, 700 ms above the requirement.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(1_000, 8.0),
            Segment(1_920, 15.0),
            Segment(3_000, 0.2),
        )

        val verdict = classify(samples)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(3_000L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `a grab from rest followed by stillness stays ordinary`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 18.0),
            Segment(3_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `a grab without a measured calm before it is not a grab`() {
        // The phone was already being handled when the window opened; the burst's first reading is
        // the window's own first moment, so no calm was measured.
        val samples = stream(
            Segment(3_000, 2.0),
            Segment(400, 16.0),
            Segment(3_000, 8.0),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `a stalled stream is ordinary even when its shape looks like a snatch`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(2_000, 8.0),
            stepMs = 200L,
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `a non-finite reading is ordinary rather than an accusation`() {
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(400, 16.0),
            Segment(2_000, 8.0),
        ).toMutableList()
        samples[10] = samples[10].copy(y = Double.NaN)

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `the onset reported is the burst start, not the sharpest reading`() {
        // Real grabs ramp: the burst opens in the noise band and only peaks 300 ms later. The grab
        // began at the burst's first reading, and that is what the challenge reports.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(300, 2.0),
            Segment(200, 16.0),
            Segment(2_000, 15.0),
            Segment(3_000, 0.2),
        )

        val verdict = classify(samples)

        assertTrue(verdict is SnatchSignaturePolicy.Verdict.Snatch, "expected a snatch, was $verdict")
        assertEquals(3_000L, (verdict as SnatchSignaturePolicy.Verdict.Snatch).grabAtMs)
    }

    @Test
    fun `a silence longer than the gap limit separates two stretches instead of bridging them`() {
        // Two 500 ms stretches at 15 m/s² with 720 ms of quiet between them sum to 1000 ms, not
        // 1720 ms: time between strong readings is added, the silence itself never is.
        val samples = stream(
            Segment(3_000, 0.2),
            Segment(1_000, 8.0),
            Segment(520, 15.0),
            Segment(700, 9.0),
            Segment(520, 15.0),
            Segment(3_000, 8.0),
            Segment(2_000, 0.2),
        )

        assertEquals(SnatchSignaturePolicy.Verdict.Ordinary, classify(samples))
    }

    @Test
    fun `a follow-up window that cannot hold the required movement is refused`() {
        // The measured signature leaves 6000 - 1000 = 5000 ms of follow-up room; requiring more
        // than that is refused when the calibration is built.
        assertFailsWith<IllegalArgumentException> {
            signature.copy(followMinMovementMs = 5_001L)
        }
    }

    private fun classify(samples: List<MotionSample>) =
        SnatchSignaturePolicy.classify(samples, motion, signature)

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
