package com.shieldra.detection

/**
 * The shipping Motion calibration: amendments A-17 and A-18 approve exactly these six numbers, and
 * this object is the only place in the app that may state them. Every other layer receives the
 * calibration as an argument, so no engineer can introduce a seventh figure or quietly retune one
 * (§37 keeps magnitude thresholds the owner's).
 */
object ApprovedMotionCalibration {
    /** A-16/A-17: the deviation that opens a movement episode. */
    const val strongDeltaMs2: Double = 4.6

    /** A-17: below this an episode ends; between the two levels the episode stays open (hysteresis). */
    const val noiseFloorDeltaMs2: Double = 0.8

    /** A-17: what makes a movement an episode at all. Not a confirmation rule — see G1-02. */
    const val minEpisodeMs: Long = 500L

    /** A-17: the absolute sampling gap the reading may not survive. It governs if the median rises. */
    const val maxSampleGapMs: Long = 100L

    /** A-17: the reporting period requested of the sensor platform. A request, never a delivered rate. */
    const val requestedReportPeriodUs: Int = 20_000

    /**
     * A-18: the stall tolerance as a multiplier of the measured median interval. Derived, not chosen —
     * at the measured ~9 ms median it asks for ~99 ms, just inside [maxSampleGapMs].
     */
    const val stallToleranceFactor: Double = 11.0

    val motionCalibration = MotionCalibration(
        strongDeltaMs2 = strongDeltaMs2,
        noiseFloorDeltaMs2 = noiseFloorDeltaMs2,
        minEpisodeMs = minEpisodeMs,
        maxSampleGapMs = maxSampleGapMs,
    )
}
