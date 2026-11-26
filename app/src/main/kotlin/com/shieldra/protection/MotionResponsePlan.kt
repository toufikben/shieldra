package com.shieldra.protection

/**
 * The exact response one confirmed Motion runs (A-33, kept by the design-B scope).
 *
 * It is pure and separate from [TableModeResponder] so a test can state, without a device, what the
 * owner picked: whether the siren sounds now, or only after the grace window that their own unlock can
 * cancel. That wait is the difference between picking your own phone up and a snatch.
 *
 * There is deliberately no lock field here, and the reason changed with A-46: this plan describes the
 * **noisy** half of the response, and the screen lock is not the owner's choice — a Motion that carries
 * the measured snatch signature is locked by [SnatchCoordinator] before this plan is consulted at all,
 * whatever the owner picked here. Keeping the lock out of the plan keeps "silent" meaning silent sound,
 * not "leave the screen open".
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
