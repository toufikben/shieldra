package com.shieldra.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The measurement A-45 D4 needs before any threshold moves: how far the gravity direction has turned
 * from the pose the phone was resting in. Orientation-free, and it must read 0 degrees for the same
 * pose flipped end over end, because a phone lying face-down is still lying.
 */
class MotionTiltProbeTest {

    private val gravityUp = doubleArrayOf(0.0, 0.0, 9.81)
    private val gravityOnSide = doubleArrayOf(9.81, 0.0, 0.0)

    @Test
    fun `the same pose is zero degrees`() {
        assertEquals(0.0, MotionTiltProbe.angleDegrees(gravityUp, gravityUp), 0.001)
    }

    @Test
    fun `a quarter turn is ninety degrees`() {
        assertEquals(90.0, MotionTiltProbe.angleDegrees(gravityUp, gravityOnSide), 0.5)
    }

    @Test
    fun `an upside down phone is one hundred and eighty degrees however heavy it reads`() {
        assertEquals(180.0, MotionTiltProbe.angleDegrees(gravityUp, doubleArrayOf(0.0, 0.0, -9.81)), 0.5)
    }

    @Test
    fun `magnitude does not care which way the axes point`() {
        assertEquals(9.81, MotionTiltProbe.magnitude(9.81, 0.0, 0.0), 0.001)
        assertEquals(9.81, MotionTiltProbe.magnitude(0.0, 0.0, 9.81), 0.001)
    }

    @Test
    fun `a baseline needs samples and returns their mean pose`() {
        assertNull(MotionTiltProbe.restingBaseline(emptyList()))
        val baseline = MotionTiltProbe.restingBaseline(
            listOf(doubleArrayOf(0.0, 0.0, 9.81), doubleArrayOf(0.0, 0.0, 9.91)),
        )
        assertEquals(9.86, baseline!![2], 0.001)
    }

    @Test
    fun `noise is spread around the baseline, not a size`() {
        val quiet = MotionTiltProbe.restingBaseline(listOf(doubleArrayOf(0.0, 0.0, 9.81), doubleArrayOf(0.0, 0.0, 9.82)))!!
        val sigma = MotionTiltProbe.noiseSigma(listOf(quiet, doubleArrayOf(0.0, 0.0, 9.83)))
        assertTrue(sigma < 0.1)
    }

    @Test
    fun `a zero-length vector never divides`() {
        assertEquals(0.0, MotionTiltProbe.angleDegrees(doubleArrayOf(0.0, 0.0, 0.0), gravityUp), 0.001)
    }
}
