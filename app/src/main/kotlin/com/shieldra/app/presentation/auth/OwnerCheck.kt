package com.shieldra.app.presentation.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * What asking the owner produced. [NoScreenLock] is reported separately because a phone without a
 * screen lock has nothing to check against: the action goes ahead, and the caller can say so.
 */
enum class OwnerCheckResult { Confirmed, NoScreenLock, Refused }

/**
 * Asks for the device's own biometric or screen-lock credential before an action that weakens
 * protection (the `DISABLE_PROTECTION` sensitive operation). Without it, whoever is holding the phone
 * could stop monitoring with a single tap.
 *
 * Only the platform prompt is used: SHIELDRA stores no PIN of its own and sees no credential.
 */
object OwnerCheck {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /**
     * Whether this phone can be asked who is holding it — the same reading the gate below uses, so the
     * Health row and the prompt cannot disagree (audit B-1). A phone with a screen lock but no
     * fingerprint still answers yes: the credential is enough.
     */
    fun canAskForTheOwner(context: Context): Boolean = OwnerGateDecision.availabilityOf(
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS),
    ) == OwnerGateAvailability.CredentialAvailable

    fun request(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (OwnerCheckResult) -> Unit,
    ) {
        when (OwnerGateDecision.availabilityOf(BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS))) {
            OwnerGateAvailability.CredentialAvailable -> Unit
            // The credential is temporarily unusable (a sensor fault, a pending security update) or the
            // platform gave an answer this build cannot classify: refuse rather than skip a check a
            // secured phone would normally ask for. "We could not tell" is not evidence of no lock.
            OwnerGateAvailability.CheckUnavailable -> {
                onResult(OwnerCheckResult.Refused)
                return
            }
            // No screen lock is set, so there is no credential to check against. Nobody standing
            // in front of the phone can change that, so the action goes ahead and is reported — by the
            // caller at the moment it happens, and permanently on the Health screen.
            OwnerGateAvailability.NoCredential -> {
                onResult(OwnerCheckResult.NoScreenLock)
                return
            }
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(OwnerCheckResult.Confirmed)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(OwnerCheckResult.Refused)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
