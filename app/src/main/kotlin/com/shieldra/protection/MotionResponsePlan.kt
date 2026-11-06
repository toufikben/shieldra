package com.shieldra.protection

/**
 * The exact response one confirmed Motion runs (A-33, kept by the design-B scope).
 *
 * It is pure and separate from [TableModeResponder] so a test can state, without a device, what the
 * owner picked: whether the siren sounds now, or only after the grace window that their own unlock can
 * cancel. That wait is the difference between picking your own phone up and a snatch.
 *
 * There is deliberately no lock field: SHIELDRA holds no device administrator and no screen-lock power
 * (design B, 2026-11-06), so a response can make noise and take evidence but can never take the screen.
 */
data class MotionResponsePlan(
    val sirenNow: Boolean,
    val waitForOwnerUnlock: Boolean,
    val sirenAfterGrace: Boolean,
) {
    companion object {
        fun of(response: ProtectionSettings.MotionResponse): MotionResponsePlan = when (response) {
            ProtectionSettings.MotionResponse.SILENT -> MotionResponsePlan(
                sirenNow = false,
                waitForOwnerUnlock = false,
                sirenAfterGrace = false,
            )

            ProtectionSettings.MotionResponse.SIREN -> MotionResponsePlan(
                sirenNow = true,
                waitForOwnerUnlock = false,
                sirenAfterGrace = false,
            )

            ProtectionSettings.MotionResponse.GRACE_THEN_SIREN -> MotionResponsePlan(
                sirenNow = false,
                waitForOwnerUnlock = true,
                sirenAfterGrace = true,
            )
        }
    }
}
