package com.shieldra.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed encryption candidate.
 *
 * It is not wired into the application graph. The caller must provide an
 * explicit policy and an explicit key alias. Software-key fallback is never
 * attempted when Android Keystore is unavailable.
 *
 * Marked [open] so that JVM unit test doubles (FakeEncryptor,
 * AadRecordingEncryptor) can override [encrypt] and [decrypt] without
 * requiring Android Keystore at test time.
 */
open class AndroidKeystoreEncryptor(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    open fun encrypt(
        plaintext: ByteArray,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray? = null,
    ): EncryptedPayload {
        validateAlias(keyAlias)
        val key = loadOrCreateKey(keyAlias, policy)
        val iv = ByteArray(EncryptionPolicy.GCM_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(EncryptionPolicy.TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(EncryptionPolicy.GCM_TAG_BITS, iv))
        associatedData?.let(cipher::updateAAD)
        return EncryptedPayload(
            version = EncryptionPolicy.PAYLOAD_VERSION,
            iv = iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    open fun decrypt(
        payload: EncryptedPayload,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray? = null,
    ): ByteArray {
        validateAlias(keyAlias)
        require(payload.version == EncryptionPolicy.PAYLOAD_VERSION) {
            "Unsupported encrypted payload version: ${payload.version}"
        }
        require(payload.iv.size == EncryptionPolicy.GCM_IV_BYTES) {
            "Invalid AES-GCM IV length"
        }
        val key = loadExistingKey(keyAlias, policy)
        val cipher = Cipher.getInstance(EncryptionPolicy.TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(EncryptionPolicy.GCM_TAG_BITS, payload.iv))
        associatedData?.let(cipher::updateAAD)
        return cipher.doFinal(payload.ciphertext)
    }

    private fun loadOrCreateKey(alias: String, policy: EncryptionPolicy): SecretKey {
        val existing = loadExistingKeyOrNull(alias)
        if (existing != null) return existing

        return try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, EncryptionPolicy.PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(policy.keySizeBits)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(policy.requireUserAuthentication)
                .setUserAuthenticationValidityDurationSeconds(policy.userAuthenticationValiditySeconds)
                .setInvalidatedByBiometricEnrollment(policy.invalidateOnBiometricEnrollment)
                .build()
            generator.init(spec)
            generator.generateKey()
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Android Keystore key generation failed; no software fallback is permitted",
                error,
            )
        }
    }

    private fun loadExistingKey(alias: String, policy: EncryptionPolicy): SecretKey {
        val key = loadExistingKeyOrNull(alias)
            ?: throw KeyMaterialUnavailableException("No Keystore key exists for alias: $alias")
        require(key.algorithm.equals(KeyProperties.KEY_ALGORITHM_AES, ignoreCase = true)) {
            "Keystore key is not AES"
        }
        require(policy.keySizeBits == 256) { "Unsupported key policy" }
        return key
    }

    private fun loadExistingKeyOrNull(alias: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(EncryptionPolicy.PROVIDER).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)
        } catch (error: Exception) {
            throw KeyMaterialUnavailableException(
                "Android Keystore is unavailable; no software fallback is permitted",
                error,
            )
        }
    }

    private fun validateAlias(alias: String) {
        require(alias.matches(ALIAS_PATTERN)) {
            "Key alias must be a non-empty namespaced identifier"
        }
    }

    companion object {
        private val ALIAS_PATTERN = Regex("[A-Za-z0-9._-]{3,128}")
    }
}

data class EncryptedPayload(
    val version: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(iv.isNotEmpty()) { "Encrypted payload IV cannot be empty" }
        require(ciphertext.isNotEmpty()) { "Encrypted payload ciphertext cannot be empty" }
    }

    /** Stable binary envelope for a future file adapter; metadata is not included. */
    fun toByteArray(): ByteArray = ByteBuffer.allocate(4 + 4 + iv.size + 4 + ciphertext.size)
        .putInt(version)
        .putInt(iv.size)
        .put(iv)
        .putInt(ciphertext.size)
        .put(ciphertext)
        .array()

    companion object {
        fun fromByteArray(value: ByteArray): EncryptedPayload {
            val buffer = ByteBuffer.wrap(value)
            val version = buffer.int
            val ivSize = buffer.int
            require(ivSize in 1..64 && buffer.remaining() >= ivSize + 4) { "Invalid encrypted payload" }
            val iv = ByteArray(ivSize).also(buffer::get)
            val ciphertextSize = buffer.int
            require(ciphertextSize > 0 && buffer.remaining() == ciphertextSize) { "Invalid encrypted payload" }
            val ciphertext = ByteArray(ciphertextSize).also(buffer::get)
            return EncryptedPayload(version, iv, ciphertext)
        }
    }
}
