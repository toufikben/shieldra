package com.shieldra.storage

/**
 * Canonical Keystore key alias and policy for evidence encryption.
 *
 * Decision §2.1: alias is per-application-installation, versioned.
 * Decision §2.2: no user authentication required (background protection).
 * Decision §2.3: StrongBox preferred, not required.
 */
object EvidenceKeyPolicy {

    /**
     * Versioned key alias (decision §2.1).
     * A future key rotation uses "shieldra_evidence_v2".
     */
    const val EVIDENCE_KEY_ALIAS: String = "shieldra_evidence_v1"

    /**
     * Production encryption policy for evidence keys (decision §2.2).
     *
     * - 256-bit AES
     * - No user authentication required (background event capture must work
     *   without a BiometricPrompt interaction)
     * - Biometric enrollment does NOT invalidate the key (background safety)
     */
    val EVIDENCE_POLICY: EncryptionPolicy = EncryptionPolicy(
        keySizeBits = 256,
        requireUserAuthentication = false,
        userAuthenticationValiditySeconds = 0,
        invalidateOnBiometricEnrollment = false,
    )
}
