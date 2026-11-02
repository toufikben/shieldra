package com.shieldra.app.presentation.auth

import androidx.biometric.BiometricManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-1: the owner gate used to treat *any* answer other than "you can be asked" as "this phone has no
 * screen lock", and let the change through without a word. That made an unrecognised platform answer
 * indistinguishable from an unlocked phone, so the mapping is tested on its own: the honest pass
 * through is only for a phone that provably has nothing to check against.
 */
class OwnerGateDecisionTest {

    @Test
    fun a_phone_that_can_check_proceeds_gets_asked() {
        assertEquals(
            OwnerGateAvailability.CredentialAvailable,
            OwnerGateDecision.availabilityOf(BiometricManager.BIOMETRIC_SUCCESS),
        )
    }

    @Test
    fun a_phone_with_no_credential_is_named_as_one_rather_than_as_a_failure() {
        assertEquals(
            OwnerGateAvailability.NoCredential,
            OwnerGateDecision.availabilityOf(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun a_credential_that_cannot_be_used_right_now_is_not_evidence_of_an_unlocked_phone() {
        // Only the codes biometric 1.1.0 is known to answer with are listed, so this test cannot fail
        // for a reason that has nothing to do with the gate.
        for (code in listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        )) {
            assertEquals(
                OwnerGateAvailability.CheckUnavailable,
                OwnerGateDecision.availabilityOf(code),
                "code $code must refuse rather than let a protection change through unproved",
            )
        }
    }

    @Test
    fun an_answer_this_build_does_not_know_refuses() {
        for (code in listOf(-999, 1, 7, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(
                OwnerGateAvailability.CheckUnavailable,
                OwnerGateDecision.availabilityOf(code),
                "an unrecognised answer must not be read as no screen lock",
            )
        }
    }
}
