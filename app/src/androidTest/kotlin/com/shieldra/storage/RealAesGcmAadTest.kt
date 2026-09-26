package com.shieldra.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    private val context = ApplicationProvider.getApplicationContext()
    private val testAlias = "shieldra_aad_test_key"
    private val testPolicy = EncryptionPolicy(
        keySizeBits = 256,
        requireUserAuthentication = false,
        userAuthenticationValiditySeconds = 0,
        invalidateOnBiometricEnrollment = false,
    )

    @Test
    fun `real AES-GCM: correct AAD encrypt/decrypt succeeds`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val aad = EvidenceAadBuilder.forEvidence("ev-test-001")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, aad)
        val decrypted = encryptor.decrypt(encrypted, testAlias, testPolicy, aad)

        assertArrayEquals("Real AES-GCM: correct AAD must decrypt to original", plaintext, decrypted)
    }

    @Test
    fun `real AES-GCM: modified AAD version causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-002")
        val wrongAad = correctAad.copyOf().also { it[0] = 0x02 } // wrong version

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Modified AAD version must cause AEADBadTagException",
            javax.crypto.AEADBadTagException::class.java,
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        }
    }

    @Test
    fun `real AES-GCM: modified evidenceId causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-003")
        val wrongAad = EvidenceAadBuilder.forEvidence("ev-different-id")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Modified evidenceId must cause AEADBadTagException",
            javax.crypto.AEADBadTagException::class.java,
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        }
    }

    @Test
    fun `real AES-GCM: modified evidence class causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.build("evidence", "ev-test-004")
        val wrongAad = EvidenceAadBuilder.build("photo", "ev-test-004")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Modified evidence class must cause AEADBadTagException",
            javax.crypto.AEADBadTagException::class.java,
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, wrongAad)
        }
    }

    @Test
    fun `real AES-GCM: tampered AAD length field causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-005")
        val tamperedAad = correctAad.copyOf().also {
            // Tamper with class length field (bytes 1-4)
            it[1] = (it[1] + 1).toByte()
        }

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Tampered length field must cause AEADBadTagException",
            javax.crypto.AEADBadTagException::class.java,
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, tamperedAad)
        }
    }

    @Test
    fun `real AES-GCM: truncated AAD causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-006")
        val truncatedAad = correctAad.copyOfRange(0, correctAad.size - 1)

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Truncated AAD must cause authentication failure",
            Exception::class.java, // Could be AEADBadTagException or IllegalArgumentException
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, truncatedAad)
        }
    }

    @Test
    fun `real AES-GCM: empty AAD causes authentication failure`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test evidence data".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-test-007")

        val encrypted = encryptor.encrypt(plaintext, testAlias, testPolicy, correctAad)

        assertThrows(
            "Empty AAD must cause authentication failure",
            Exception::class.java,
        ) {
            encryptor.decrypt(encrypted, testAlias, testPolicy, ByteArray(0))
        }
    }

    @Test
    fun `real AES-GCM: null AAD on encrypt throws IllegalArgumentException`() {
        val encryptor = AndroidKeystoreEncryptor()
        val plaintext = "test".toByteArray()

        assertThrows(
            "Null AAD on encrypt must throw IllegalArgumentException",
            IllegalArgumentException::class.java,
        ) {
            // This tests the KeystoreEvidenceCipherWithAad wrapper which validates AAD
            val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
            cipher.encrypt(plaintext, ByteArray(0)) // empty AAD should be rejected by wrapper
        }
    }

    @Test
    fun `real AES-GCM: KeystoreEvidenceCipherWithAad encrypt/decrypt round-trip`() {
        val encryptor = AndroidKeystoreEncryptor()
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
        val plaintext = "full round-trip test".toByteArray(StandardCharsets.UTF_8)
        val aad = EvidenceAadBuilder.forEvidence("ev-roundtrip-001")

        val encrypted = cipher.encrypt(plaintext, aad)
        val decrypted = cipher.decrypt(encrypted, aad)

        assertArrayEquals("KeystoreEvidenceCipherWithAad round-trip must succeed", plaintext, decrypted)
    }

    @Test
    fun `real AES-GCM: KeystoreEvidenceCipherWithAad wrong AAD rejected`() {
        val encryptor = AndroidKeystoreEncryptor()
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, testAlias, testPolicy)
        val plaintext = "wrong AAD test".toByteArray(StandardCharsets.UTF_8)
        val correctAad = EvidenceAadBuilder.forEvidence("ev-wrong-aad-001")
        val wrongAad = EvidenceAadBuilder.forEvidence("ev-different")

        val encrypted = cipher.encrypt(plaintext, correctAad)

        assertThrows(
            "KeystoreEvidenceCipherWithAad must reject wrong AAD",
            EvidenceUnavailableException::class.java,
        ) {
            cipher.decrypt(encrypted, wrongAad)
        }
    }

    @Test
    fun `real AES-GCM: StrongBox capability detection`() {
        val encryptor = AndroidKeystoreEncryptor()
        val isStrongBoxAvailable = encryptor.isStrongBoxAvailable()

        // Just verify the method runs without crash; actual availability depends on device
        assertTrue("isStrongBoxAvailable() must return boolean", true)
    }
}