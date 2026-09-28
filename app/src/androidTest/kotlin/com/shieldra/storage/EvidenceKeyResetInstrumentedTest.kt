package com.shieldra.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * REAL factory-reset runtime test (decision §5.7, amendment A-2).
 *
 * The JVM suite proves the reset *orchestration* against an injected fake
 * keyDeletion callback. This instrumented test proves the part no JVM run can:
 * that `deleteEvidenceKeyIrreversibly` actually removes a **real Android
 * Keystore** evidence key, and that every ciphertext ever sealed under it is
 * permanently unreadable afterwards with no software fallback (§2.5). It runs
 * the production `FactoryReset` wired to the production encryptor, so the
 * confirmation gate, the file/metadata removal and the Keystore deletion are
 * exercised together exactly as a real reset would.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceKeyResetInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val policy = EncryptionPolicy(
        keySizeBits = 256,
        requireUserAuthentication = false,
        userAuthenticationValiditySeconds = 0,
        invalidateOnBiometricEnrollment = false,
    )
    private lateinit var evidenceDirectory: File
    private lateinit var metadataDatabase: File
    private lateinit var encryptor: AndroidKeystoreEncryptor

    @Before
    fun setUp() {
        evidenceDirectory = File(context.noBackupFilesDir, "reset_runtime_test").apply {
            deleteRecursively()
            mkdirs()
        }
        metadataDatabase = File(context.noBackupFilesDir, "reset_runtime_meta.db").apply { delete() }
        encryptor = AndroidKeystoreEncryptor(applicationContext = context)
    }

    private fun reset(): FactoryReset = FactoryReset(
        evidenceDirectory = evidenceDirectory,
        metadataDatabase = metadataDatabase,
        keyDeletion = encryptor::deleteEvidenceKeyIrreversibly,
    )

    /** Create the real Keystore key by sealing evidence under it, as a capture would. */
    private fun sealEvidenceUnderV1(): Pair<String, EncryptedPayload> {
        val id = "ev-reset-runtime"
        val plaintext = "device-bound evidence that must become unreadable".toByteArray(StandardCharsets.UTF_8)
        val aad = RotationAadVersions.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1)
        val payload = encryptor.encrypt(plaintext, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, policy, aad)
        File(evidenceDirectory, "$id.shieldra").writeBytes(payload.toByteArray())
        metadataDatabase.writeBytes("room rows".toByteArray())
        return id to payload
    }

    @Test
    fun a_confirmed_reset_permanently_destroys_the_real_keystore_evidence_key() {
        val (id, payload) = sealEvidenceUnderV1()
        val aad = RotationAadVersions.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1)

        // Pre-reset sanity on device: the key exists and reads its own evidence.
        val readable = encryptor.decrypt(payload, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, policy, aad)
        assertTrue(
            "the Keystore key must read its own evidence before the reset",
            String(readable, StandardCharsets.UTF_8).startsWith("device-bound evidence"),
        )

        val result = reset().execute(
            aliases = listOf(
                EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
                EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2,
            ),
            confirmed = true,
        )

        assertTrue("a confirmed reset of reachable storage must complete, got: $result",
            result is FactoryResetResult.Completed)
        assertFalse("evidence files are removed on device", File(evidenceDirectory, "$id.shieldra").exists())
        assertFalse("room metadata is removed on device", metadataDatabase.exists())

        // A-2 device proof: after the real key is deleted, the previously readable
        // ciphertext is now permanently unreadable — the Keystore reports no key and
        // there is no software fallback (§2.5).
        assertThrows(
            "the deleted Keystore key must never decrypt again; no fallback is permitted",
            KeyMaterialUnavailableException::class.java,
        ) { encryptor.decrypt(payload, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, policy, aad) }

        // Idempotent: deleting the already-removed key reports absence, not error.
        assertFalse(
            "deleting an absent Keystore key is a no-op, not a failure (§5.7 idempotence)",
            encryptor.deleteEvidenceKeyIrreversibly(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS),
        )
    }

    @Test
    fun an_unconfirmed_reset_never_touches_the_real_keystore_key() {
        val (id, payload) = sealEvidenceUnderV1()
        val aad = RotationAadVersions.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1)

        val result = reset().execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS),
            confirmed = false,
        )

        assertTrue("§5.7 requires explicit confirmation; nothing authorized, got: $result",
            result is FactoryResetResult.Refused)
        assertTrue("evidence must survive an unconfirmed reset", File(evidenceDirectory, "$id.shieldra").exists())
        assertTrue(
            "the Keystore key must survive an unconfirmed reset",
            String(encryptor.decrypt(payload, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, policy, aad), StandardCharsets.UTF_8)
                .startsWith("device-bound evidence"),
        )
    }
}
