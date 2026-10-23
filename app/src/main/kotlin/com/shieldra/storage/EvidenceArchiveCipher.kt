package com.shieldra.storage

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The §5.5 export archive cipher: encrypts an evidence export under an owner-chosen passphrase so it
 * can be opened off-device (the at-rest Keystore key cannot leave the phone, so the export cannot use
 * it). A random salt and IV are prepended to the ciphertext; the key is PBKDF2-derived, the payload
 * sealed with AES-256-GCM, so a wrong passphrase or any tampering fails the tag instead of yielding
 * plaintext. Pure JCE — unit-tested on the JVM without a device.
 */
object EvidenceArchiveCipher {

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128

    /** Returns salt ‖ iv ‖ ciphertext(+tag). */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return salt + iv + cipher.doFinal(plaintext)
    }

    /** Recovers the plaintext; throws if the passphrase is wrong or the blob was tampered with. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > SALT_LEN + IV_LEN) { "export blob too short" }
        val salt = blob.copyOfRange(0, SALT_LEN)
        val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ciphertext = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
