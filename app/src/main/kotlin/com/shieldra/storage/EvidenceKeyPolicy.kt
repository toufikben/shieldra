package com.shieldra.storage

/**
 * Canonical Keystore key alias and policy for evidence encryption.
 *
 * Decision §2.1: alias is per-application-installation, versioned.
 * Decision §2.2: no user authentication required (background protection).
 * Decision §2.3: StrongBox preferred, not required.
 * Decision §3.1–§3.2: rotation from v1 to v2; every evidence tracks
 * keyVersion and aadVersion.
 */
object EvidenceKeyPolicy {

    /**
     * Versioned key alias (decision §2.1).
     * The active rotation target uses "shieldra_evidence_v2".
     */
    const val EVIDENCE_KEY_ALIAS: String = "shieldra_evidence_v1"

    /** Key version of [EVIDENCE_KEY_ALIAS] (decision §3.2). */
    const val KEY_VERSION_V1: String = "v1"

    /** Rotation target key alias (decision §3.1). */
    const val EVIDENCE_KEY_ALIAS_V2: String = "shieldra_evidence_v2"

    /** Key version of [EVIDENCE_KEY_ALIAS_V2] (decision §3.2). */
    const val KEY_VERSION_V2: String = "v2"

    /** Canonical AAD version carried by v1 evidence (decision §1.1, §3.2). */
    const val AAD_VERSION_V1: String = "AAD-v1"

    /**
     * Rotation trigger (decision §3.1): 90 days OR explicit manual trigger.
     * Expressed in whole days; callers compare elapsed time explicitly.
     */
    const val ROTATION_INTERVAL_DAYS: Long = 90L

    /**
     * Returns the Keystore alias for a stored key version.
     * Unknown versions fail closed: there is no implicit alias mapping.
     */
    fun aliasForKeyVersion(keyVersion: String): String = when (keyVersion) {
        KEY_VERSION_V1 -> EVIDENCE_KEY_ALIAS
        KEY_VERSION_V2 -> EVIDENCE_KEY_ALIAS_V2
        else -> throw IllegalArgumentException("Unsupported evidence key version: $keyVersion")
    }

    /**
     * Returns true when [ROTATION_INTERVAL_DAYS] elapsed since [lastRotationEpochMs].
     * A manual trigger bypasses this check entirely (decision §3.1).
     */
    fun rotationDue(lastRotationEpochMs: Long, nowEpochMs: Long): Boolean {
        require(lastRotationEpochMs >= 0) { "Last rotation timestamp cannot be negative" }
        require(nowEpochMs >= 0) { "Current timestamp cannot be negative" }
        if (nowEpochMs < lastRotationEpochMs) return false
        val elapsedDays = (nowEpochMs - lastRotationEpochMs) / MILLIS_PER_DAY
        return elapsedDays >= ROTATION_INTERVAL_DAYS
    }

    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

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
