package com.shieldra.app.presentation.auth

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

    fun request(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (OwnerCheckResult) -> Unit,
    ) {
        when (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            // The credential is temporarily unusable (a sensor fault, a pending security update):
            // refuse rather than skip a check a secured phone would normally ask for.
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                onResult(OwnerCheckResult.Refused)
                return
            }
            // No screen lock is set, so there is no credential to check against. Nobody standing
            // in front of the phone can change that, so the action goes ahead and is reported.
            else -> {
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
