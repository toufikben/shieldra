package com.shieldra.detection

import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Gravity-relative measurement for the Motion guard (A-45 D4).
 *
 * The shipped rule measures how hard the phone is moved, which cannot tell a lift from a bump: the
 * literature on a single force threshold puts sensitivity and specificity in a trade-off no constant
 * resolves. A change in the *direction* of gravity is a different fact — a phone lying still on its
 * side and a phone lying still flat have the same magnitude and a 90 degree difference in pose — and it
 * is what "someone picked it up" actually means. AOSP's own tilt detector is defined exactly this way:
 * a two-second average gravity vector that moved by 35 degrees or more.
 *
 * Pure, and deliberately unused by the shipped rule until the owner's handset has been recorded and
 * measured, which is what says what the constant should be.
 */
object MotionTiltProbe {

    const val STANDARD_GRAVITY_MS2 = 9.80665

    fun magnitude(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    /** The angle in degrees between two acceleration vectors; 0 when either is degenerate. */
    fun angleDegrees(base: DoubleArray, sample: DoubleArray): Double {
        if (base.size != 3 || sample.size != 3) return 0.0
        val baseMag = magnitude(base[0], base[1], base[2])
        val sampleMag = magnitude(sample[0], sample[1], sample[2])
        if (baseMag < 0.001 || sampleMag < 0.001) return 0.0
        val dot = (base[0] * sample[0] + base[1] * sample[1] + base[2] * sample[2]) / (baseMag * sampleMag)
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))
    }

    /** The resting pose: the mean vector of the samples taken while the phone lay still. */
    fun restingBaseline(samples: List<DoubleArray>): DoubleArray? {
        if (samples.isEmpty()) return null
        val axes = DoubleArray(3)
        for (sample in samples) {
            if (sample.size != 3) return null
            for (i in 0..2) axes[i] += sample[i]
        }
        return DoubleArray(3) { axes[it] / samples.size }
    }

    /**
     * The scatter around the resting pose, in m/s2 — the noise floor of this table at this time of day.
     * A threshold that sits under it is a threshold that fires on a passing tram.
     */
    fun noiseSigma(samples: List<DoubleArray>): Double {
        val baseline = restingBaseline(samples) ?: return 0.0
        if (samples.size < 2) return 0.0
        val sum = samples.sumOf { sample ->
            (0..2).sumOf { axis -> (sample[axis] - baseline[axis]) * (sample[axis] - baseline[axis]) }
        }
        return sqrt(sum / max(1, samples.size * 3))
    }
}
