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
 * this file. Stage 3 measures it there, exactly as Q-G1 measured the Motion levels, and only then
 * may a shipping object exist. Every other layer receives the calibration as an argument.
 */
data class SnatchSignatureCalibration(
    /** A deliberate grab reaches at least this deviation for one reading. */
    val grabPeakDeltaMs2: Double,
    /** How long the device must have been still before that reading for it to count as a grab. */
    val calmBeforeGrabMs: Long,
    /** Where the grab's own sharp part ends; the follow-up is counted from there. */
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
 * The pure snatch signature (A-47). A confirmed Motion is not a snatch by itself: the phoning owner
 * also runs, lifts the phone and rides. This object reads the same sample window the Motion guard
 * confirmed and answers one question — did a hand grab from rest turn into sustained strong motion
 * inside the window?
 *
 * The shape is the owner's rule, not an engineer's: [calmBeforeGrabMs] of stillness (every reading
 * below the Motion noise floor), a sharp onset at [SnatchSignatureCalibration.grabPeakDeltaMs2],
 * and then, counted only after the sharp part ends, at least [SnatchSignatureCalibration.followMinMovementMs]
 * of movement at [SnatchSignatureCalibration.followMinPeakDeltaMs2] with no silent gap beyond the
 * limit. A run for the bus starts with the phone already moving, a lift ends in quiet, and a
 * motorcycle ride never has the calm-then-grab onset; all three stay [Verdict.Ordinary].
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

        /** A grab from rest became locomotion: the owner may be asked to prove the phone is theirs. */
        data class Snatch(val grabAtMs: Long) : Verdict
    }

    fun classify(
        samples: List<MotionSample>,
        motionCalibration: MotionCalibration,
        signature: SnatchSignatureCalibration,
    ): Verdict {
        if (samples.size < 2) return Verdict.Ordinary

        val deviations = DoubleArray(samples.size)
        for (index in samples.indices) {
            val sample = samples[index]
            if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.z.isFinite()) return Verdict.Ordinary
            if (index > 0) {
                val gap = sample.elapsedMs - samples[index - 1].elapsedMs
                if (gap <= 0L || gap > motionCalibration.maxSampleGapMs) return Verdict.Ordinary
            }
            deviations[index] = gravityDeltaMs2(sample.x, sample.y, sample.z)
        }

        val onset = findGrabOnset(samples, deviations, motionCalibration.noiseFloorDeltaMs2, signature)
            ?: return Verdict.Ordinary
        val onsetAtMs = samples[onset].elapsedMs
        val followFromMs = onsetAtMs + signature.grabMaxDurationMs
        val followUntilMs = onsetAtMs + signature.followWindowMs

        var movedMs = 0L
        var previousQualifyingAt: Long? = null
        for (index in samples.indices) {
            val at = samples[index].elapsedMs
            if (at < followFromMs) continue
            if (at > followUntilMs) break
            if (deviations[index] < signature.followMinPeakDeltaMs2) continue
            val previous = previousQualifyingAt
            if (previous != null) {
                val gap = at - previous
                if (gap <= signature.followMaxGapMs) movedMs += gap
            }
            previousQualifyingAt = at
        }
        return if (movedMs >= signature.followMinMovementMs) {
            Verdict.Snatch(onsetAtMs)
        } else {
            Verdict.Ordinary
        }
    }

    /**
     * The first reading at the grab level with a full, measured calm behind it. A candidate whose
     * calm is not inside the window is skipped rather than accepted: "I did not see the phone being
     * still" is not the same as "the phone was still".
     */
    private fun findGrabOnset(
        samples: List<MotionSample>,
        deviations: DoubleArray,
        noiseFloorDeltaMs2: Double,
        signature: SnatchSignatureCalibration,
    ): Int? {
        val windowStartMs = samples.first().elapsedMs
        for (index in samples.indices) {
            if (deviations[index] < signature.grabPeakDeltaMs2) continue
            val at = samples[index].elapsedMs
            if (at - windowStartMs < signature.calmBeforeGrabMs) continue
            val calmStartMs = at - signature.calmBeforeGrabMs
            var calm = true
            var cursor = index - 1
            while (cursor >= 0 && samples[cursor].elapsedMs >= calmStartMs) {
                if (deviations[cursor] >= noiseFloorDeltaMs2) {
                    calm = false
                    break
                }
                cursor--
            }
            if (calm) return index
        }
        return null
    }
}
