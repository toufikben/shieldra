package com.shieldra.detection

import com.shieldra.domain.GuardObservation

/**
 * The one composition the approved calibration is used through: what the sensor delivered becomes a
 * stream verdict and a window reading together, so neither half can be consulted without the other.
 *
 * G1-G4 built the three parts separately and A-12 kept them apart on purpose. This class joins them
 * in the owner's order and adds no rule of its own: the numbers come from the caller, the judgement
 * stays in [MotionWindowEvaluator], the stream description stays in [MotionSignalNormalizer], and
 * the bridge to the approved engine stays [MotionGuardObservation] — which is still the only place
 * a stream verdict can lower what an observation claims.
 */
class MotionGuardPipeline(
    private val calibration: MotionCalibration,
    private val stallToleranceFactor: Double,
) {
    private val normalizer = MotionSignalNormalizer(stallToleranceFactor)
    private val evaluator = MotionWindowEvaluator(calibration)

    fun observe(samples: List<MotionSample>): GuardObservation.Motion =
        MotionGuardObservation.of(evaluator.read(samples), normalizer.normalize(samples).quality)
}
