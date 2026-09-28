package com.shieldra.storage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * REAL AES-GCM authentication tests.
 *
 * These tests MUST run on an Android device/emulator (API 23+) to verify
 * actual AES-GCM authentication behavior with Android Keystore.
 *
 * They are NOT JVM unit tests and will be skipped if no Android runtime is available.
 *
 * Run with: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shieldra.storage.RealAesGcmAadTest
 */
@RunWith(AndroidJUnit4::class)
class RealAesGcmAadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testAlias = "shieldra_aad_test_key"
    private val testPolicy = EncryptionPolicy(
        keySizeBits = 256,
        requireUserAuthentication = false,
        userAuthenticationValiditySeconds = 0,
        invalidateOnBiometricEnrollment = false,
    )

    @Test
    fun real_aes_gcm_correct_aad_encrypt_decrypt_succeeds() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val aad = EvidenceAadBuilder.forEvidence("ev-test-001")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, aad)
        val decrypted = encryptor.decrypt(encrypted, testAlias, testPolicy, aad)

        assertArrayEquals("Real AES-GCM: correct AAD must decrypt to original", plaintext, decrypted)
    }

    @Test
    fun real_aes_gcm_modified_aad_version_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-002")
        val wrongAad = correctAad.copyOf().also { it[0] = 0x02 } // wrong version

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        } catch (e: javax.crypto.AEADBadTagException) {
            caught = true
        }
        assertTrue("Modified AAD version must cause AEADBadTagException", caught)
    }

    @Test
    fun real_aes_gcm_modified_evidence_id_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-003")
        val wrongAad = EvidenceAadBuilder.forEvidence("ev-different-id")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        } catch (e: javax.crypto.AEADBadTagException) {
            caught = true
        }
        assertTrue("Modified evidenceId must cause AEADBadTagException", caught)
    }

    @Test
    fun real_aes_gcm_modified_evidence_class_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.build("evidence", "ev-test-004")
        val wrongAad = EvidenceAadBuilder.build("photo", "ev-test-004")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        } catch (e: javax.crypto.AEADBadTagException) {
            caught = true
        }
        assertTrue("Modified evidence class must cause AEADBadTagException", caught)
    }

    @Test
    fun real_aes_gcm_tampered_aad_length_field_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-005")
        val tamperedAad = correctAad.copyOf().also {
            // Tamper with class length field (bytes 1-4)
            it[1] = (it[1] + 1).toByte()
        }

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, tamperedAad)
        } catch (e: javax.crypto.AEADBadTagException) {
            caught = true
        }
        assertTrue("Tampered length field must cause AEADBadTagException", caught)
    }

    @Test
    fun real_aes_gcm_truncated_aad_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-006")
        val truncatedAad = correctAad.copyOfRange(0, correctAad.size - 1)

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, truncatedAad)
        } catch (e: Exception) {
            caught = true
        }
        assertTrue("Truncated AAD must cause authentication failure", caught)
    }

    @Test
    fun real_aes_gcm_empty_aad_causes_authentication_failure() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-007")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        var caught = false
        try {
            encryptor.decrypt(encrypted, testAlias, testPolicy, ByteArray(0))
        } catch (e: Exception) {
            caught = true
        }
        assertTrue("Empty AAD must cause authentication failure", caught)
    }

    @Test
    fun real_aes_gcm_cipher_rejects_empty_aad_on_encrypt() {
        val encryptor = AndroidKeystoreEncryptor()
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
        val plaintext = "test".toByteArray()

        var caught = false
        try {
            cipher.encrypt(plaintext, ByteArray(0)) // empty AAD should be rejected by wrapper
        } catch (e: IllegalArgumentException) {
            caught = true
        }
        assertTrue("Empty AAD on encrypt must throw IllegalArgumentException", caught)
    }

    @Test
    fun real_aes_gcm_cipher_encrypt_decrypt_round_trip() {
        val encryptor = AndroidKeystoreEncryptor()
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
        val plaintext = "full round-trip test".toByteArray(StandardCharsets.UTF_8)
        val aad = EvidenceAadBuilder.forEvidence("ev-roundtrip-001")

        val encrypted = cipher.encrypt(plaintext, aad)
        val decrypted = cipher.decrypt(encrypted, aad)

        assertArrayEquals("KeystoreEvidenceCipherWithAad round-trip must succeed", plaintext, decrypted)
    }

    @Test
    fun real_aes_gcm_cipher_wrong_aad_rejected() {
        val encryptor = AndroidKeystoreEncryptor()
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
        val plaintext = "wrong AAD test".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-wrong-aad-001")
        val wrongAad = EvidenceAadBuilder.forEvidence("ev-different")

        val encrypted = cipher.encrypt(plaintext, correctAad)

        var caught = false
        try {
            cipher.decrypt(encrypted, wrongAad)
        } catch (e: EvidenceUnavailableException) {
            caught = true
        }
        assertTrue("KeystoreEvidenceCipherWithAad must reject wrong AAD", caught)
    }

    @Test
    fun real_aes_gcm_strongbox_capability_detection() {
        val expected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val withContext = AndroidKeystoreEncryptor(applicationContext = context)

        assertEquals(
            "StrongBox detection must query PackageManager.FEATURE_STRONGBOX_KEYSTORE " +
                "at runtime, never infer availability from SDK level alone (decision 2-3)",
            expected,
            withContext.isStrongBoxAvailable(),
        )

        assertFalse(
            "without a context, detection must fail closed instead of guessing from SDK level",
            AndroidKeystoreEncryptor().isStrongBoxAvailable(),
        )
    }
}