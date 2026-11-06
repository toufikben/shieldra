package com.shieldra.app.presentation.auth

import androidx.biometric.BiometricManager

/**
 * Why the owner gate could not ask, decided in one place (audit B-1) so the prompt and the Health
 * check can never disagree about what "this phone has no screen lock" means.
 */
enum class OwnerGateAvailability {
    /** The phone has a credential, so the owner can be asked to prove it. */
    CredentialAvailable,

    /** No screen lock is set: there is nothing on this phone to check against. */
    NoCredential,

    /** The check itself could not be trusted — a sensor fault, a pending update, an unknown answer. */
    CheckUnavailable,
}

/**
 * The owner gate's reading of the platform: one total mapping from an answer code to an outcome, with
 * no prompt and no context involved, so the rule can be argued in a JVM test instead of on a phone.
 *
 * The gate exists because whoever holds the phone should not be able to stop the guards or redirect
 * the alerts. But a phone with no screen lock has no answer to give: failing closed there would lock
 * the owner out of their own settings, and the app would look broken rather than unsafe. So the gate
 * goes ahead and says so — at the moment of the change, and permanently on the Health screen.
 * Anything short of a credential being *absent* is treated as unavailable and refuses, because "we
 * could not tell" is not evidence that the phone is unlocked.
 */
object OwnerGateDecision {
    /** Maps the platform's answer to the gate's three outcomes. Unrecognised answers refuse. */
    fun availabilityOf(canAuthenticate: Int): OwnerGateAvailability = when (canAuthenticate) {
        BiometricManager.BIOMETRIC_SUCCESS -> OwnerGateAvailability.CredentialAvailable
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> OwnerGateAvailability.NoCredential
        else -> OwnerGateAvailability.CheckUnavailable
    }
}
