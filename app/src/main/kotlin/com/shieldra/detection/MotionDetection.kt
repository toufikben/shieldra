package com.shieldra.detection

import com.shieldra.domain.ApprovedGuardRules
import com.shieldra.domain.GuardObservation
import com.shieldra.domain.SensorQuality
import java.time.Duration

/**
 * Batch G / G1-02: the measurement half of Motion Guard.
 *
 * The domain engine already holds the owner's approved *time* rule; this file produces the
 * observation that rule consumes. It is pure JVM code with no Android import on purpose: the
 * adapter that gathers samples lives in `MotionSensorAdapter.kt`, and amendment A-12 keeps the
 * judgement and the Android surface in separate files so neither can quietly become the other.
 * The magnitude maths it shares with that layer is in `MotionSignal.kt`.
 */

data class MotionSample(
    val elapsedMs: Long,
    val x: Double,
    val y: Double,
    val z: Double,
)

/**
 * Every magnitude number a Motion judgement needs. It has no defaults on purpose: the shipping
 * values are Q-G1, and a threshold written here would be a security policy invented by an
 * engineer instead of the owner.
 */
data class MotionCalibration(
    val strongDeltaMs2: Double,
    val noiseFloorDeltaMs2: Double,
    val minEpisodeMs: Long,
    val maxSampleGapMs: Long,
) {
    init {
        require(strongDeltaMs2 > 0.0) { "The strong movement level must be above zero" }
        require(noiseFloorDeltaMs2 >= 0.0) { "The noise floor cannot be negative" }
        require(strongDeltaMs2 > noiseFloorDeltaMs2) {
            "Hysteresis needs the strong level above the noise floor, otherwise an episode can never end"
        }
        require(minEpisodeMs >= 0) { "The minimum episode length cannot be negative" }
        require(maxSampleGapMs > 0) { "The sampling gap limit must be positive" }
    }
}

/**
 * A movement that lasted long enough to be taken seriously. Both [startMs] and [endMs] are times
 * at which a sample really was above the noise floor, so a reported movement never claims time
 * the sensor did not observe.
 */
data class MotionEpisode(
    val startMs: Long,
    val endMs: Long,
    val peakDeltaMs2: Double,
) {
    init {
        require(endMs >= startMs) { "An episode cannot end before it started" }
    }

    val durationMs: Long get() = endMs - startMs
}

sealed interface MotionReading {
    /** Fewer than two samples: nothing was measured, which is not the same as a still device. */
    data object Insufficient : MotionReading

    /** The samples cannot be trusted, so no Motion claim may be built from them. */
    data class Unreliable(val reason: String) : MotionReading

    data class Movements(val episodes: List<MotionEpisode>) : MotionReading
}

class MotionWindowEvaluator(private val calibration: MotionCalibration) {

    fun read(samples: List<MotionSample>): MotionReading {
        if (samples.size < 2) return MotionReading.Insufficient

        val deviations = ArrayList<Double>(samples.size)
        for (index in samples.indices) {
            val sample = samples[index]
            if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite()) {
                return MotionReading.Unreliable("sample at ${sample.elapsedMs} ms is not a finite acceleration")
            }
            if (index > 0) {
                val gap = sample.elapsedMs - samples[index - 1].elapsedMs
                if (gap <= 0L) {
                    return MotionReading.Unreliable(
                        "samples at ${samples[index - 1].elapsedMs} ms and ${sample.elapsedMs} ms are not in increasing time order",
                    )
                }
                if (gap > calibration.maxSampleGapMs) {
                    return MotionReading.Unreliable("sampling stopped for $gap ms before ${sample.elapsedMs} ms")
                }
            }
            deviations += deviation(sample)
        }

        val episodes = mutableListOf<MotionEpisode>()
        var start = -1
        var lastAboveFloor = -1
        var peak = 0.0
        for (index in deviations.indices) {
            val value = deviations[index]
            if (start < 0) {
                if (value >= calibration.strongDeltaMs2) {
                    start = index
                    lastAboveFloor = index
                    peak = value
                }
            } else if (value > calibration.noiseFloorDeltaMs2) {
                lastAboveFloor = index
                if (value > peak) peak = value
            } else {
                keepEpisode(episodes, samples, start, lastAboveFloor, peak)
                start = -1
            }
        }
        if (start >= 0) {
            keepEpisode(episodes, samples, start, lastAboveFloor, peak)
        }
        return MotionReading.Movements(episodes)
    }

    private fun keepEpisode(
        episodes: MutableList<MotionEpisode>,
        samples: List<MotionSample>,
        start: Int,
        lastAboveFloor: Int,
        peak: Double,
    ) {
        val episode = MotionEpisode(
            startMs = samples[start].elapsedMs,
            endMs = samples[lastAboveFloor].elapsedMs,
            peakDeltaMs2 = peak,
        )
        if (episode.durationMs >= calibration.minEpisodeMs) episodes += episode
    }

    /**
     * Magnitude minus rest gravity: orientation-free, so a phone held on its side measures the
     * same movement it would measure flat. The maths itself is shared with the G3 signal layer in
     * `MotionSignal.kt`, so the two halves of Motion cannot drift apart.
     */
    private fun deviation(sample: MotionSample): Double = gravityDeltaMs2(sample.x, sample.y, sample.z)
}

/**
 * The only bridge from what was measured to what the approved engine may decide. It never upgrades
 * sensor quality and never reports a movement the samples did not contain. Slice G4 added the cap
 * that was missing: what the signal layer measured about the *stream* ([MotionSignalQuality], from
 * delivery timing and the values themselves) arrives as a required input, so a stream that stalled
 * or carried a value that is not an acceleration cannot enter the approved rule as valid sensor
 * data just because the samples it did deliver looked like a movement.
 */
object MotionGuardObservation {

    fun of(
        reading: MotionReading,
        streamQuality: MotionSignalQuality,
        window: Duration = ApprovedGuardRules.motionRepetitionWindow,
    ): GuardObservation.Motion {
        // Refused before anything is counted, so an untrusted stream cannot leak a duration, an
        // occurrence, or a repetition window into the owner's rule on its way to being discarded.
        val refused = when (streamQuality) {
            MotionSignalQuality.EMPTY -> refusal(SensorQuality.UNAVAILABLE)
            MotionSignalQuality.INVALID, MotionSignalQuality.IRREGULAR -> refusal(SensorQuality.NOISY)
            MotionSignalQuality.USABLE -> null
        }
        if (refused != null) return refused

        return when (reading) {
            MotionReading.Insufficient -> refusal(SensorQuality.UNAVAILABLE)
            is MotionReading.Unreliable -> refusal(SensorQuality.NOISY)
            is MotionReading.Movements -> observed(reading.episodes, window)
        }
    }

    /** The one shape every refusal takes: nothing may be claimed, and the quality says why. */
    private fun refusal(quality: SensorQuality) = GuardObservation.Motion(
        duration = Duration.ZERO,
        suspiciousOccurrences = 0,
        sensorQuality = quality,
    )

    private fun observed(
        episodes: List<MotionEpisode>,
        window: Duration,
    ): GuardObservation.Motion {
        val latestEnd = episodes.maxOfOrNull { it.endMs }
        if (latestEnd == null) {
            return GuardObservation.Motion(
                duration = Duration.ZERO,
                suspiciousOccurrences = 0,
                sensorQuality = SensorQuality.VALID,
            )
        }

        val counted = episodes.filter { it.endMs >= latestEnd - window.toMillis() }
        return GuardObservation.Motion(
            duration = Duration.ofMillis(counted.maxOf { it.durationMs }),
            suspiciousOccurrences = counted.size,
            sensorQuality = SensorQuality.VALID,
            // A span between two movements is the only thing that can back the repetition rule;
            // reporting one for a single movement would invent a window the samples never had.
            suspiciousOccurrencesWithin = if (counted.size >= 2) {
                Duration.ofMillis(latestEnd - counted.minOf { it.startMs })
            } else {
                null
            },
        )
    }
}
