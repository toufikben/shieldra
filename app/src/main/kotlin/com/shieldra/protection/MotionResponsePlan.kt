package com.shieldra.protection

/**
 * The exact response one confirmed Motion runs (amendment A-43).
 *
 * It is pure and separate from [TableModeResponder] so a test can state, without a device, the two
 * promises the owner asked for: every confirmed lift ends with the screen locked, and only the
 * grace response is allowed to wait for the owner's own unlock first — that wait is the difference
 * between picking your own phone up and a snatch.
 */
data class MotionResponsePlan(
    val sirenNow: Boolean,
    val waitForOwnerUnlock: Boolean,
    val sirenAfterGrace: Boolean,
    val lockAtOnce: Boolean,
    val lockAfterGrace: Boolean,
) {
    companion object {
        fun of(response: ProtectionSettings.MotionResponse): MotionResponsePlan = when (response) {
            ProtectionSettings.MotionResponse.SILENT -> MotionResponsePlan(
                sirenNow = false,
                waitForOwnerUnlock = false,
                sirenAfterGrace = false,
                lockAtOnce = true,
                lockAfterGrace = false,
            )

            ProtectionSettings.MotionResponse.SIREN -> MotionResponsePlan(
                sirenNow = true,
                waitForOwnerUnlock = false,
                sirenAfterGrace = false,
                lockAtOnce = true,
                lockAfterGrace = false,
            )

            ProtectionSettings.MotionResponse.GRACE_THEN_SIREN -> MotionResponsePlan(
                sirenNow = false,
                waitForOwnerUnlock = true,
                sirenAfterGrace = true,
                lockAtOnce = false,
                lockAfterGrace = true,
            )
        }
    }
}
