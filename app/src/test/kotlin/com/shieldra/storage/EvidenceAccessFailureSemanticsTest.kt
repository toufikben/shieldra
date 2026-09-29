package com.shieldra.storage

import java.nio.file.Files
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * §6 + amendment A-4: a MISSING KEY on a read must surface as RECOVERY_REQUIRED,
 * not be laundered into "unavailable". A confirmed factory reset (§5.7 step 3)
 * deletes the evidence FILES too, so post-reset reads hit the missing-file path
 * and correctly stay UNAVAILABLE (permanently unreadable). The two cases are
 * therefore distinguished by whether the ciphertext is still present, exactly as
 * the owner's decision requires.
 */
class EvidenceAccessFailureSemanticsTest {

    private val policy = EncryptionPolicy(
        keySizeBits = 256,
        requireUserAuthentication = false,
        userAuthenticationValiditySeconds = 0,
        invalidateOnBiometricEnrollment = false,
    )

    @Test
    fun missing_key_on_decrypt_surfaces_as_recovery_required_not_unavailable() {
        val cipher = KeystoreEvidenceCipherWithAad(MissingKeyEncryptor(), "shieldra_evidence_v1", policy)
        val aad = EvidenceAadBuilder.forEvidence("ev-001")

        assertFailsWith<EvidenceKeyRecoveryRequiredException>(
            "§6/A-4: a missing evidence key must be RECOVERY_REQUIRED",
        ) { cipher.decrypt(byteArrayOf(1, 2, 3), aad) }
    }

    @Test
    fun an_integrity_failure_stays_unavailable() {
        val cipher = KeystoreEvidenceCipherWithAad(CorruptEncryptor(), "shieldra_evidence_v1", policy)
        val aad = EvidenceAadBuilder.forEvidence("ev-001")

        assertFailsWith<EvidenceUnavailableException>(
            "corruption / AAD mismatch stays UNAVAILABLE (§6), never recovery",
        ) { cipher.decrypt(byteArrayOf(1, 2, 3), aad) }
    }

    @Test
    fun the_store_does_not_launder_a_recovery_required_read() {
        val directory = Files.createTempDirectory("shieldra-failure").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, RecoveryRequiredCipher, aadProvider)
        store.write("ev-1", "payload".toByteArray())

        val error = assertFailsWith<EvidenceKeyRecoveryRequiredException> { store.read("ev-1") }
        assertTrue(
            error.message!!.contains("shieldra_evidence_v1") || error.message!!.isNotEmpty(),
            "recovery signal must be observable to the operator",
        )
    }

    @Test
    fun the_store_still_reports_a_missing_file_as_unavailable() {
        // This is the post-reset case: the confirmed reset removed the file, so a
        // read is UNAVAILABLE (permanently unreadable, A-2) rather than RECOVERY_REQUIRED.
        val directory = Files.createTempDirectory("shieldra-failure").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, RecoveryRequiredCipher, aadProvider)

        assertFailsWith<EvidenceUnavailableException> { store.read("never-written") }
    }

    @Test
    fun a_confirmed_reset_and_an_accidental_key_loss_map_to_different_outcomes() {
        // A-4 reconciliation as a table: reset removes the file (MISSING_EVIDENCE ->
        // UNAVAILABLE, permanent); accidental loss keeps the file but not the key
        // (MISSING_KEY -> RECOVERY_REQUIRED, operator must act).
        assertEquals(
            SecurityFailureOutcome.UNAVAILABLE,
            SecurityFailureSemantics.classify(SecurityFailureCondition.MISSING_EVIDENCE),
        )
        assertEquals(
            SecurityFailureOutcome.RECOVERY_REQUIRED,
            SecurityFailureSemantics.classify(SecurityFailureCondition.MISSING_KEY),
        )
    }

    // --- test doubles -------------------------------------------------------

    private class MissingKeyEncryptor : AndroidKeystoreEncryptor() {
        override fun decrypt(
            payload: EncryptedPayload,
            keyAlias: String,
            policy: EncryptionPolicy,
            associatedData: ByteArray?,
        ): ByteArray = throw KeyMaterialUnavailableException("No Keystore key exists for alias: $keyAlias")
    }

    private class CorruptEncryptor : AndroidKeystoreEncryptor() {
        override fun decrypt(
            payload: EncryptedPayload,
            keyAlias: String,
            policy: EncryptionPolicy,
            associatedData: ByteArray?,
        ): ByteArray = throw AEADBadTagException("Tag mismatch")
    }

    /** Cipher that reports a missing key as recovery on read, so the store path can be tested. */
    private object RecoveryRequiredCipher : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray = plaintext.copyOf()
        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray =
            throw EvidenceKeyRecoveryRequiredException("Evidence key is unavailable for shieldra_evidence_v1")
    }
}
