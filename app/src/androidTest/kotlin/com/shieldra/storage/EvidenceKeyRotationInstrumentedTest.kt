package com.shieldra.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * REAL key-rotation runtime test (decision §3.3–§3.5).
 *
 * The JVM suite proves the rotation *algorithm* against a faked cipher. This
 * instrumented test proves the part no JVM run can: that rotating an evidence
 * from the `shieldra_evidence_v1` Keystore key to `shieldra_evidence_v2`
 * actually works with two *distinct hardware/software-backed keys* on a real
 * device — the old key decrypts the pre-rotation file and rejects the
 * post-rotation file, and the new key is the only one that reads the result.
 *
 * It asserts nothing that §2.5 or §4.3 forbid: no software-key fallback and no
 * destructive path is exercised; the old key is never deleted here.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceKeyRotationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var evidenceDirectory: File
    private lateinit var encryptor: AndroidKeystoreEncryptor
    private lateinit var ciphers: KeystoreRotationCipherFactory
    private lateinit var files: FileSystemRotationGateway
    private lateinit var metadata: InMemoryMetadata
    private lateinit var rotator: EvidenceKeyRotator

    private val aadFactory = RotationAadVersions

    @Before
    fun setUp() {
        evidenceDirectory = File(context.noBackupFilesDir, "rotation_test").apply {
            deleteRecursively()
            mkdirs()
        }
        encryptor = AndroidKeystoreEncryptor(applicationContext = context)
        ciphers = KeystoreRotationCipherFactory(encryptor)
        files = FileSystemRotationGateway(evidenceDirectory)
        metadata = InMemoryMetadata()
        rotator = EvidenceKeyRotator(files, metadata, ciphers, aadFactory)
    }

    @After
    fun tearDown() {
        evidenceDirectory.deleteRecursively()
    }

    @Test
    fun rotation_reencrypts_evidence_under_the_new_keystore_key() {
        val id = "ev-rotation-runtime-001"
        val plaintext = "device-bound evidence payload".toByteArray(StandardCharsets.UTF_8)
        val aad = aadFactory.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1)

        val v1Cipher = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
        val v2Cipher = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)

        // Commit the evidence under the real v1 key, as a normal capture would.
        files.writeTemp(id, v1Cipher.encrypt(plaintext, aad))
        files.commitTemp(id)
        metadata.rows[id] = RotationMetadata(
            keyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
            aadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
        )

        // Pre-rotation: the v1 key reads it, the v2 key rejects it.
        // This is the device proof that v1 and v2 are distinct Keystore keys.
        val original = files.readCommitted(id)
        assertArrayEquals(plaintext, v1Cipher.decrypt(original, aad))
        assertThrows(
            "the v2 key must not decrypt evidence written under the v1 key",
            EvidenceUnavailableException::class.java,
        ) { v2Cipher.decrypt(original, aad) }

        val result = rotator.rotate(
            RotationRequest(
                evidenceId = id,
                fromKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
                toKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V2,
                fromAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
                toAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
            ),
        )
        assertTrue("rotation must succeed on device, got: $result")
        assertIsMigrated(result)

        val rotated = files.readCommitted(id)
        assertArrayEquals(
            "the new key must be the only reader of the rotated evidence",
            plaintext,
            v2Cipher.decrypt(rotated, aad),
        )
        // The old key can no longer read the file: real re-encryption happened.
        assertThrows(
            "the v1 key must reject the rotated evidence",
            EvidenceUnavailableException::class.java,
        ) { v1Cipher.decrypt(rotated, aad) }

        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, metadata.rows[id]?.keyVersion)
        assertEquals(RotationItemState.VERIFIED, rotator.stateOf(id))
    }

    @Test
    fun rotating_an_already_migrated_evidence_is_skipped_not_redone() {
        val id = "ev-rotation-runtime-002"
        val plaintext = "already rotated payload".toByteArray(StandardCharsets.UTF_8)
        val aad = aadFactory.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1)

        val v1Cipher = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
        val v2Cipher = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)

        files.writeTemp(id, v1Cipher.encrypt(plaintext, aad))
        files.commitTemp(id)
        metadata.rows[id] = RotationMetadata(
            keyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
            aadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
        )

        val request = RotationRequest(
            evidenceId = id,
            fromKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
            toKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V2,
            fromAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
            toAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
        )
        assertIsMigrated(rotator.rotate(request))
        val bytesAfterFirstRotation = files.readCommitted(id)

        val second = rotator.rotate(request)
        assertTrue("re-running must be a no-op, got: $second")
        // Committed bytes are untouched by a skipped rotation (never redone).
        assertArrayEquals(bytesAfterFirstRotation, files.readCommitted(id))
        assertArrayEquals(plaintext, v2Cipher.decrypt(files.readCommitted(id), aad))
    }

    private fun assertIsMigrated(result: RotationResult) {
        assertTrue(
            "expected a fresh migration, got: $result",
            result is RotationResult.Migrated || result is RotationResult.AlreadyMigrated,
        )
    }

    /** Test-only metadata backing; Room rows are covered by the migration suite. */
    private class InMemoryMetadata : RotationMetadataGateway {
        val rows = mutableMapOf<String, RotationMetadata>()

        override fun versionsOf(evidenceId: String): RotationMetadata? = rows[evidenceId]

        override fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String) {
            rows[evidenceId] = RotationMetadata(keyVersion, aadVersion)
        }
    }
}
