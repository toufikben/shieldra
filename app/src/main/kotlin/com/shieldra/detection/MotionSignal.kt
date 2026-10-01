package com.shieldra.detection

import kotlin.math.sqrt

/**
 * Batch G / slice G3: the sampling-and-quality half of the Motion path, and the only Android
 * entry point the approved scope allows.
 *
 * Amendment A-12 fixes the boundary this file sits inside. [MotionSensorAdapter] is the source: it
 * registers the continuous accelerometer and hands over what the sensor said, stamped with the
 * sensor's own clock. [MotionSignalNormalizer] is the description: magnitude, the interval that
 * actually elapsed between readings, and a quality verdict computed from the data. Neither of them
 * decides anything — there is no Security Event, no severity, no cooldown, no threshold that means
 * "attack", and no notification or storage write reachable from this package. Signal data is not a
 * confirmed security event; validation, confirmation and severity belong to later, separately
 * approved layers.
 *
 * Two rules from that amendment are load-bearing here:
 * - the one-shot `SIGNIFICANT_MOTION` tripwire is *reported as available or absent* and never
 *   armed. A trigger sensor does not belong in a continuous sampling layer, and the platform
 *   refuses it through `registerListener` anyway (measured in slice G2).
 * - nothing that was *measured on RMX3910* is a constant in this code. The reporting period is a
 *   caller-supplied request, and the rate is computed from delivered timestamps because a phone
 *   delivered around 106 Hz for a 20 ms request.
 */

/** Rest gravity, the reference a deviation is measured against. A physical constant, not a threshold. */
internal const val STANDARD_GRAVITY_MS2 = 9.80665

internal fun accelerationMagnitudeMs2(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

/** Orientation-free: the same movement measures the same whether the phone is flat or on its side. */
internal fun gravityDeltaMs2(x: Double, y: Double, z: Double): Double =
      kotlin.math.abs(accelerationMagnitudeMs2(x, y, z) - STANDARD_GRAVITY_MS2)

/**
 * One readable accelerometer reading, described rather than judged. [gravityDeltaMs2] is how far
 * the vector was from rest; it is not a statement that anything happened.
 */
data class MotionSignalPoint(
    val elapsedMs: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val magnitudeMs2: Double,
    val gravityDeltaMs2: Double,
)

/**
 * What the stream is made of, as observed. The names describe the samples, never a security
 * conclusion: `IRREGULAR` means the delivery timing stopped matching itself, not that someone is
 * moving the device.
 */
enum class MotionSignalQuality {
    /** Fewer than two readable samples: nothing was measured, which is not the same as a still device. */
    EMPTY,

    /** The stream cannot be believed: a value that is not an acceleration, or time that ran backwards. */
    INVALID,

    /** Readable samples, but delivery stalled beyond the tolerance the caller supplied. */
    IRREGULAR,

    /** Readable samples, in order, with intervals inside the tolerance. Still only signal data. */
    USABLE,
}

/**
 * The whole of G3's output: signal data and the measurements needed to describe it. Every number
 * except [qualityReasons]'s tolerance factor was computed from the samples that arrived.
 */
data class MotionSignal(
    val points: List<MotionSignalPoint>,
    val inputSampleCount: Int,
    val droppedSampleCount: Int,
    val medianIntervalMs: Double?,
    val measuredRateHz: Double?,
    val longestIntervalMs: Long?,
    val quality: MotionSignalQuality,
    val qualityReasons: List<String>,
)

/**
 * Describes a stream of [MotionSample] without deciding anything about it.
 *
 * [stallToleranceFactor] is a multiplier on the median interval, not a millisecond figure copied
 * off a device: it has no default because the tolerance that is honest for one sampling rate is
 * wrong for another, and the shipped value belongs with the shipping calibration (Q-G1).
 */
class MotionSignalNormalizer(private val stallToleranceFactor: Double) {

    init {
        require(stallToleranceFactor.isFinite() && stallToleranceFactor > 1.0) {
            "The stall tolerance must be a finite factor above 1.0, otherwise normal frame jitter " +
                "would be reported as a delivery stall"
        }
    }

    fun normalize(samples: List<MotionSample>): MotionSignal {
        val points = ArrayList<MotionSignalPoint>(samples.size)
        val reasons = mutableListOf<String>()
        var dropped = 0
        for ((index, sample) in samples.withIndex()) {
            if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite()) {
                dropped++
                reasons += "sample $index at ${sample.elapsedMs} ms is not a finite acceleration"
                continue
            }
            val previous = points.lastOrNull()
            if (previous != null) {
                val interval = sample.elapsedMs - previous.elapsedMs
                if (interval == 0L) {
                    dropped++
                    reasons += "sample $index shares the millisecond ${sample.elapsedMs} ms with the one before it; " +
                        "the sensor clock cannot put the two in order"
                    continue
                }
                if (interval < 0L) {
                    dropped++
                    reasons += "sample $index at ${sample.elapsedMs} ms arrived out of time order after ${previous.elapsedMs} ms"
                    continue
                }
            }
            points += MotionSignalPoint(
                elapsedMs = sample.elapsedMs,
                x = sample.x,
                y = sample.y,
                z = sample.z,
                magnitudeMs2 = accelerationMagnitudeMs2(sample.x, sample.y, sample.z),
                gravityDeltaMs2 = gravityDeltaMs2(sample.x, sample.y, sample.z),
            )
        }

        val intervals = if (points.size >= 2) {
            points.zipWithNext { earlier, later -> later.elapsedMs - earlier.elapsedMs }
        } else {
            emptyList()
        }
        val median = if (intervals.isEmpty()) null else medianOf(intervals)
        val longest = intervals.maxOrNull()
        val rate = median?.takeIf { it > 0.0 }?.let { 1000.0 / it }

        val quality = when {
            dropped > 0 -> MotionSignalQuality.INVALID
            points.size < 2 -> MotionSignalQuality.EMPTY.also {
                reasons += "Nothing was measured: ${points.size} readable sample(s) carry no interval"
            }
            median != null && median > 0.0 && longest != null && longest > median * stallToleranceFactor ->
                MotionSignalQuality.IRREGULAR.also {
                    reasons += "delivery stalled for $longest ms against a measured median interval of $median ms"
                }
            else -> MotionSignalQuality.USABLE
        }

        return MotionSignal(
            points = points,
            inputSampleCount = samples.size,
            droppedSampleCount = dropped,
            medianIntervalMs = median,
            measuredRateHz = rate,
            longestIntervalMs = longest,
            quality = quality,
            qualityReasons = reasons,
        )
    }

    private fun medianOf(values: List<Long>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle].toDouble()
    }
}
