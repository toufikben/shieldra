package com.shieldra.protection

import com.shieldra.detection.MotionCalibration
import com.shieldra.detection.MotionSample
import com.shieldra.detection.gravityDeltaMs2

/**
 * The levels that separate a snatch from ordinary movement (A-47, owner decision 2026-11-10).
 *
 * There are deliberately no defaults. The owner's rule is that a sharp grab after a measured calm,
 * followed within seconds by sustained strong locomotion, is what opens the challenge; whether a
 * hand grab, a run and a motorcycle really produce those numbers is a property of the phone, not of
 * this file. Stage 3 measured it on the owner's phone and the result is
 * [ApprovedSnatchSignatureCalibration.signature]; every other layer receives the calibration as an
 * argument.
 */
data class SnatchSignatureCalibration(
    /** A deliberate grab brings at least one reading of its burst to this deviation. */
    val grabPeakDeltaMs2: Double,
    /** How long the device must have been still before the grab's burst begins. */
    val calmBeforeGrabMs: Long,
    /** Where the grab's own sharp part ends, counted from the burst's first reading. */
    val grabMaxDurationMs: Long,
    /** The sustained movement after the grab must reach this deviation. */
    val followMinPeakDeltaMs2: Double,
    /** How far after the grab the follow-up may arrive. */
    val followWindowMs: Long,
    /** How much measured movement inside that window makes the follow-up real. */
    val followMinMovementMs: Long,
    /** A silence longer than this separates two follow-up stretches, it does not bridge them. */
    val followMaxGapMs: Long,
) {
    init {
        require(grabPeakDeltaMs2 > 0.0) { "The grab level must be above zero" }
        require(calmBeforeGrabMs >= 0L) { "The calm interval cannot be negative" }
        require(grabMaxDurationMs > 0L) { "The grab must last a measurable moment" }
        require(followMinPeakDeltaMs2 > 0.0) { "The follow-up level must be above zero" }
        require(followWindowMs > grabMaxDurationMs) {
            "The follow-up window must extend beyond the grab, or nothing can follow it"
        }
        require(followMinMovementMs > 0L) { "The follow-up must require some movement" }
        require(followMinMovementMs <= followWindowMs - grabMaxDurationMs) {
            "The required movement cannot exceed the room the follow-up window leaves"
        }
        require(followMaxGapMs > 0L) { "The follow-up gap limit must be positive" }
    }
}

/**
 * The shipping signature, measured from RAW streams on the owner's phone (A-47/3, 2026-11-11).
 *
 * The measurement matrix, at exactly the follow-up definition below (window from the burst's start
 * +1 s to +6 s, movement summed over readings at 14 m/s² with gaps up to 500 ms):
 *
 * - ordinary lifts and walks: 0 ms of follow-up movement;
 * - ordinary pocket walking, three windows: 548, 236 and 804 ms (the worst is 804);
 * - a grab followed by running, two windows: 4772 and 4488 ms;
 * - a grab followed by a vehicle, two windows: 2272 and 1911 ms (the weakest is 1911).
 *
 * The 1200 ms requirement sits 396 ms above the worst ordinary shape and 711 ms below the weakest
 * snatch shape. These are measurements, not preferences; a new number needs a new measurement set,
 * which is the only thing that may become this object.
 */
object ApprovedSnatchSignatureCalibration {
    val signature = SnatchSignatureCalibration(
        grabPeakDeltaMs2 = 3.0,
        calmBeforeGrabMs = 2_500L,
        grabMaxDurationMs = 1_000L,
        followMinPeakDeltaMs2 = 14.0,
        followWindowMs = 6_000L,
        followMinMovementMs = 1_200L,
        followMaxGapMs = 500L,
    )
}

/**
 * The pure snatch signature (A-47). A confirmed Motion is not a snatch by itself: the phoning owner
 * also runs, lifts the phone and rides. This object reads the same sample window the Motion guard
 * confirmed and answers one question — did a hand grab from rest turn into sustained strong motion
 * inside the window?
 *
 * The shape is the owner's rule, not an engineer's: [SnatchSignatureCalibration.calmBeforeGrabMs]
 * of stillness (every reading below the Motion noise floor), then a burst of movement — readings at
 * or above the noise floor, with no silent gap beyond the limit — that carries at least one sharp
 * reading at [SnatchSignatureCalibration.grabPeakDeltaMs2]. A real grab ramps through the noise band
 * before it peaks, so the burst's first reading is the grab's onset, not the sharp reading itself.
 * From [SnatchSignatureCalibration.grabMaxDurationMs] after that onset and inside
 * [SnatchSignatureCalibration.followWindowMs], the window must hold at least
 * [SnatchSignatureCalibration.followMinMovementMs] of movement at
 * [SnatchSignatureCalibration.followMinPeakDeltaMs2], with no silence beyond
 * [SnatchSignatureCalibration.followMaxGapMs] bridging it.
 *
 * Every burst is tried in order until one qualifies. A released cable makes a motionless spike whose
 * follow-up is stillness; that burst spends its own question and the walking that starts later is
 * still judged on its own measured calm. A run for the bus starts with the phone already moving, a
 * lift ends in quiet, and a motorcycle ride has no calm-then-grab onset; all three stay
 * [Verdict.Ordinary].
 *
 * Fail-closed means fail to the *base* behaviour, never to the challenge: an untrusted stream, a
 * short window, a non-finite reading or a non-increasing clock is [Verdict.Ordinary]. The base Motion
 * response still records its evidence; only the lock, demand and ladder are withheld.
 *
 * Pure and Android-free, and the deviation maths is the Motion layer's own [gravityDeltaMs2], so the
 * two cannot drift apart.
 */
object SnatchSignaturePolicy {

    sealed interface Verdict {
        /** The base Motion response, and nothing more: no lock, no demand, no ladder. */
        data object Ordinary : Verdict

        /** A grab from rest became locomotion; [grabAtMs] is the first reading of the grab's burst. */
        data class Snatch(val grabAtMs: Long) : Verdict
    }

    fun classify(
        samples: List<MotionSample>,
        motionCalibration: MotionCalibration,
        signature: SnatchSignatureCalibration,
    ): Verdict {
        val deviations = deviationsOrNull(samples, motionCalibration) ?: return Verdict.Ordinary

        val bursts = movementBursts(samples, deviations, motionCalibration.noiseFloorDeltaMs2, signature.followMaxGapMs)
        for (burst in bursts) {
            if (!hasGrabReading(burst, deviations, signature.grabPeakDeltaMs2)) continue
            if (!hasMeasuredCalm(samples, deviations, burst.first, motionCalibration.noiseFloorDeltaMs2, signature)) {
                continue
            }
            val burstStartMs = samples[burst.first].elapsedMs
            if (followMovementMs(samples, deviations, burstStartMs, signature) >= signature.followMinMovementMs) {
                return Verdict.Snatch(burstStartMs)
            }
        }
        return Verdict.Ordinary
    }

    /**
     * The last moment at which [classify] can still change its answer for this window (A-47 D3).
     *
     * A live service asks the question when the Motion guard confirms a movement, which is a few
     * seconds after the burst began — before the follow-up window has been fully measured. Answering
     * "ordinary" then would only mean "not measured yet", and for the weakest measured snatch shape it
     * would be wrong: at the confirmation the visible movement is still below the requirement. This
     * returns the moment the last burst that could still become a snatch has had its whole follow-up
     * window measured, so the caller can wait exactly that long and no longer.
     *
     * The only bursts that hold the answer open are the ones that could still qualify: a burst with a
     * grab reading whose calm was fully measured. Everything else — a short window, an untrusted
     * stream, a burst without a sharp reading — is answerable at once, so [atMs] comes back unchanged.
     */
    fun verdictFixedAtMs(
        samples: List<MotionSample>,
        motionCalibration: MotionCalibration,
        signature: SnatchSignatureCalibration,
        atMs: Long,
    ): Long {
        val deviations = deviationsOrNull(samples, motionCalibration) ?: return atMs
        val bursts = movementBursts(samples, deviations, motionCalibration.noiseFloorDeltaMs2, signature.followMaxGapMs)
        var fixedAtMs = atMs
        for (burst in bursts) {
            if (!hasGrabReading(burst, deviations, signature.grabPeakDeltaMs2)) continue
            if (!hasMeasuredCalm(samples, deviations, burst.first, motionCalibration.noiseFloorDeltaMs2, signature)) {
                continue
            }
            val dueMs = samples[burst.first].elapsedMs + signature.followWindowMs
            if (dueMs > fixedAtMs) fixedAtMs = dueMs
        }
        return fixedAtMs
    }

    /**
     * The one reading of the stream both functions share: deviations from rest gravity, or null when
     * the window cannot be trusted (too short, non-finite, or a clock/gap the Motion layer itself
     * refuses). Null is fail-closed in both callers.
     */
    private fun deviationsOrNull(
        samples: List<MotionSample>,
        motionCalibration: MotionCalibration,
    ): DoubleArray? {
        if (samples.size < 2) return null
        val deviations = DoubleArray(samples.size)
        for (index in samples.indices) {
            val sample = samples[index]
            if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite()) return null
            if (index > 0) {
                val gap = sample.elapsedMs - samples[index - 1].elapsedMs
                if (gap <= 0L || gap > motionCalibration.maxSampleGapMs) return null
            }
            deviations[index] = gravityDeltaMs2(sample.x, sample.y, sample.z)
        }
        return deviations
    }

    /**
     * The stretches of movement in the window: runs of readings at or above the Motion noise floor,
     * where a silent gap longer than [maxGapMs] ends a stretch and the next reading opens another.
     */
    private fun movementBursts(
        samples: List<MotionSample>,
        deviations: DoubleArray,
        noiseFloorDeltaMs2: Double,
        maxGapMs: Long,
    ): List<IntRange> {
        val bursts = mutableListOf<IntRange>()
        var first = -1
        var last = -1
        for (index in samples.indices) {
            if (deviations[index] < noiseFloorDeltaMs2) continue
            if (first < 0) {
                first = index
            } else if (samples[index].elapsedMs - samples[last].elapsedMs > maxGapMs) {
                bursts += first..last
                first = index
            }
            last = index
        }
        if (first >= 0) bursts += first..last
        return bursts
    }

    private fun hasGrabReading(burst: IntRange, deviations: DoubleArray, grabPeakDeltaMs2: Double): Boolean {
        for (index in burst) {
            if (deviations[index] >= grabPeakDeltaMs2) return true
        }
        return false
    }

    /**
     * The calm is measured, never assumed: the whole interval before the burst must be inside the
     * window and every reading in it below the noise floor. A burst whose calm is not inside the
     * window is skipped rather than accepted: "I did not see the phone being still" is not the same
     * as "the phone was still".
     */
    private fun hasMeasuredCalm(
        samples: List<MotionSample>,
        deviations: DoubleArray,
        burstFirst: Int,
        noiseFloorDeltaMs2: Double,
        signature: SnatchSignatureCalibration,
    ): Boolean {
        val burstStartMs = samples[burstFirst].elapsedMs
        if (burstStartMs - samples.first().elapsedMs < signature.calmBeforeGrabMs) return false
        val calmStartMs = burstStartMs - signature.calmBeforeGrabMs
        var cursor = burstFirst - 1
        while (cursor >= 0 && samples[cursor].elapsedMs >= calmStartMs) {
            if (deviations[cursor] >= noiseFloorDeltaMs2) return false
            cursor--
        }
        return true
    }

    /**
     * The probe's own movement sum, applied to this burst's follow-up window, so the classifier and
     * the measurement cannot drift apart: time between strong readings is added, silence is not.
     */
    private fun followMovementMs(
        samples: List<MotionSample>,
        deviations: DoubleArray,
        burstStartMs: Long,
        signature: SnatchSignatureCalibration,
    ): Long {
        val fromMs = burstStartMs + signature.grabMaxDurationMs
        val untilMs = burstStartMs + signature.followWindowMs
        var movedMs = 0L
        var previousQualifyingAt: Long? = null
        for (index in samples.indices) {
            val at = samples[index].elapsedMs
            if (at < fromMs) continue
            if (at > untilMs) break
            if (deviations[index] < signature.followMinPeakDeltaMs2) continue
            val previous = previousQualifyingAt
            if (previous != null) {
                val gap = at - previous
                if (gap <= signature.followMaxGapMs) movedMs += gap
            }
            previousQualifyingAt = at
        }
        return movedMs
    }
}
