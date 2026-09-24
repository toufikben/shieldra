package com.shieldra.storage

/**
 * Evidence cipher that binds each encrypt/decrypt operation to the
 * canonical AAD for the supplied evidenceId (decision §1.2, §1.3).
 *
 * Rules enforced:
 * - AAD is ALWAYS provided; there is no optional or bypass path.
 * - An evidenceId mismatch (wrong AAD during decrypt) surfaces as
 *   [EvidenceUnavailableException], not a raw cryptographic exception.
 * - No software-key fallback is attempted (decision §2.5).
 */
class KeystoreEvidenceCipherWithAad(
    private val encryptor: AndroidKeystoreEncryptor,
    private val keyAlias: String,
    private val policy: EncryptionPolicy,
    private val evidenceId: String,
) : EvidenceCipher {

    init {
        require(evidenceId.isNotBlank()) { "evidenceId must not be blank" }
        require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
    }

    /**
     * Encrypts [plaintext] using the canonical AAD for [evidenceId].
     *
     * AAD is always built and passed; encrypt() will never be called
     * without valid AAD (decision §1.2 — fail closed if AAD cannot be built).
     */
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val aad = buildAad()
        return encryptor
            .encrypt(plaintext, keyAlias, policy, aad)
            .toByteArray()
    }

    /**
     * Decrypts [ciphertext] using the canonical AAD for [evidenceId].
     *
     * If the ciphertext was encrypted with a different evidenceId (AAD
     * mismatch), GCM authentication will fail and the exception is wrapped
     * as [EvidenceUnavailableException] without exposing internal state
     * (decision §1.3).
     */
    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val aad = buildAad()
        return try {
            encryptor.decrypt(
                EncryptedPayload.fromByteArray(ciphertext),
                keyAlias,
                policy,
                aad,
            )
        } catch (error: EvidenceUnavailableException) {
            throw error
        } catch (error: Exception) {
            // Wrap raw crypto failure (including GCM tag mismatch on AAD
            // mismatch) as EvidenceUnavailableException; do not leak
            // key state, ciphertext validity, or actual stored evidenceId.
            throw EvidenceUnavailableException(
                "Evidence is unavailable: authentication or integrity check failed",
                error,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the canonical AAD; fails closed (throws) if construction fails.
     * Never returns null and never falls back to an empty byte array.
     */
    private fun buildAad(): ByteArray =
        EvidenceAadBuilder.forEvidence(evidenceId)
}
