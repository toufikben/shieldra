package com.shieldra.storage

/**
 * Evidence cipher that performs AES-GCM encryption/decryption with mandatory AAD.
 *
 * Rules enforced (decision §1.2, §1.3):
 * - AAD is ALWAYS required; there is no optional or bypass path.
 * - An AAD mismatch (wrong AAD during decrypt) surfaces as [EvidenceUnavailableException].
 * - No software-key fallback is attempted (decision §2.5).
 *
 * This class does NOT build AAD internally; the caller must provide the canonical AAD.
 * Use [EvidenceAadBuilder] to construct canonical AAD from evidenceId.
 */
class KeystoreEvidenceCipherWithAad(
    private val encryptor: AndroidKeystoreEncryptor,
    private val keyAlias: String,
    private val policy: EncryptionPolicy,
) : EvidenceCipher {

    init {
        require(keyAlias.isNotBlank()) { "keyAlias must not be blank" }
    }

    /**
     * Encrypts [plaintext] using the provided canonical AAD.
     *
     * @param plaintext The evidence plaintext to encrypt.
     * @param aad Canonical AAD bytes (version || class || evidenceId).
     * @throws IllegalArgumentException if AAD is null or empty.
     */
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(aad.isNotEmpty()) { "AAD must not be empty for evidence encryption" }
        return encryptor
            .encrypt(plaintext, keyAlias, policy, aad)
            .toByteArray()
    }

    /**
     * Decrypts [ciphertext] using the provided canonical AAD.
     *
     * @param ciphertext The encrypted payload envelope.
     * @param aad Canonical AAD bytes (version || class || evidenceId).
     * @throws EvidenceUnavailableException if authentication fails (wrong AAD, corruption, etc.).
     * @throws IllegalArgumentException if AAD is null or empty.
     */
    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(aad.isNotEmpty()) { "AAD must not be empty for evidence decryption" }
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
}