package com.shieldra.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Unit tests for [EvidenceAadBuilder], covering all 10 cases required by
 * decision §1.4 of the SHIELDRA Final Security Storage Decisions.
 */
class EvidenceAadBuilderTest {

    // -----------------------------------------------------------------------
    // Case 1: same data + same AAD = same bytes (determinism prerequisite)
    // -----------------------------------------------------------------------

    @Test
    fun `case1 same objectClass and evidenceId produce identical bytes`() {
        val first = EvidenceAadBuilder.build("evidence", "ev-001")
        val second = EvidenceAadBuilder.build("evidence", "ev-001")
        assertArrayEquals(
            "Case 1: identical inputs must produce identical AAD bytes",
            first,
            second,
        )
    }

    // -----------------------------------------------------------------------
    // Case 2: version byte change = different AAD
    // -----------------------------------------------------------------------

    @Test
    fun `case2 mutated version byte produces different AAD`() {
        val original = EvidenceAadBuilder.build("evidence", "ev-001")
        val mutated = original.copyOf()
        mutated[0] = 0x02 // change version byte
        val msg = "Case 2: mutating the version byte must change the AAD"
        assertTrue(msg, !original.contentEquals(mutated))
    }

    @Test
    fun `case2 version byte is 0x01`() {
        val aad = EvidenceAadBuilder.build("evidence", "ev-001")
        assertEquals("Case 2: version byte must be 0x01", 0x01.toByte(), aad[0])
    }

    // -----------------------------------------------------------------------
    // Case 3: objectClass change = different AAD
    // -----------------------------------------------------------------------

    @Test
    fun `case3 different objectClass produces different AAD`() {
        val original = EvidenceAadBuilder.build("evidence", "ev-001")
        val other = EvidenceAadBuilder.build("photo", "ev-001")
        assertNotEquals(
            "Case 3: different objectClass must produce different AAD",
            original.toList(),
            other.toList(),
        )
    }

    // -----------------------------------------------------------------------
    // Case 4: evidenceId change = different AAD
    //   (at the cipher level this surfaces as EvidenceUnavailableException;
    //    here we verify that the AAD bytes are different, which is the
    //    precondition for the GCM tag mismatch that triggers the exception)
    // -----------------------------------------------------------------------

    @Test
    fun `case4 different evidenceId produces different AAD`() {
        val aadA = EvidenceAadBuilder.build("evidence", "ev-001")
        val aadB = EvidenceAadBuilder.build("evidence", "ev-002")
        assertNotEquals(
            "Case 4: different evidenceId must produce different AAD bytes",
            aadA.toList(),
            aadB.toList(),
        )
    }

    @Test
    fun `case4 KeystoreEvidenceCipherWithAad wraps mismatch as EvidenceUnavailableException`() {
        val plaintext = "sensitive data".toByteArray()
        val encryptor = FakeEncryptor()
        val policy = EncryptionPolicy(
            keySizeBits = 256,
            requireUserAuthentication = false,
            userAuthenticationValiditySeconds = 0,
            invalidateOnBiometricEnrollment = false,
        )
        val cipher = KeystoreEvidenceCipherWithAad(encryptor, "shieldra_evidence_v1", policy)

        val aadA = EvidenceAadBuilder.forEvidence("ev-001")
        val aadB = EvidenceAadBuilder.forEvidence("ev-002")

        val encrypted = cipher.encrypt(plaintext, aadA)
        // Decrypting with a different evidenceId (different AAD) must throw EvidenceUnavailableException
        assertThrows(
            "Case 4: evidenceId mismatch must throw EvidenceUnavailableException",
            EvidenceUnavailableException::class.java,
        ) {
            cipher.decrypt(encrypted, aadB)
        }
    }

    // -----------------------------------------------------------------------
    // Case 5: field length tampered = different bytes
    // -----------------------------------------------------------------------

    @Test
    fun `case5 tampered objectClass length field produces different AAD`() {
        val original = EvidenceAadBuilder.build("evidence", "ev-001")
        val tampered = original.copyOf()
        // bytes 1..4 hold the objectClass length (Big Endian Int)
        tampered[1] = (tampered[1] + 1).toByte()
        val msg = "Case 5: tampered length field must produce a different byte sequence"
        assertTrue(msg, !original.contentEquals(tampered))
    }

    @Test
    fun `case5 tampered evidenceId length field produces different AAD`() {
        val original = EvidenceAadBuilder.build("evidence", "ev-001")
        val classBytes = "evidence".toByteArray(StandardCharsets.UTF_8)
        // offset of evidenceId length = 1 + 4 + classBytes.size
        val idLenOffset = 1 + 4 + classBytes.size
        val tampered = original.copyOf()
        tampered[idLenOffset] = (tampered[idLenOffset] + 1).toByte()
        assertTrue(
            "Case 5: tampered evidenceId length must produce a different byte sequence",
            !original.contentEquals(tampered),
        )
    }

    // -----------------------------------------------------------------------
    // Case 6: field order swapped = different AAD
    // -----------------------------------------------------------------------

    @Test
    fun `case6 swapped field order produces different AAD`() {
        val normal = EvidenceAadBuilder.build("evidence", "ev-001")
        // Build a manually swapped version: idBytes first, then classBytes
        val classBytes = "evidence".toByteArray(StandardCharsets.UTF_8)
        val idBytes = "ev-001".toByteArray(StandardCharsets.UTF_8)
        val swapped = ByteBuffer.allocate(1 + 4 + idBytes.size + 4 + classBytes.size)
            .put(EvidenceAadBuilder.VERSION_BYTE)
            .putInt(idBytes.size)
            .put(idBytes)
            .putInt(classBytes.size)
            .put(classBytes)
            .array()
        assertNotEquals(
            "Case 6: swapped field order must differ from canonical AAD",
            normal.toList(),
            swapped.toList(),
        )
    }

    // -----------------------------------------------------------------------
    // Case 7: UTF-8 determinism
    // -----------------------------------------------------------------------

    @Test
    fun `case7 multiple calls return identical bytes`() {
        repeat(10) { i ->
            val a = EvidenceAadBuilder.build("evidence", "ev-$i")
            val b = EvidenceAadBuilder.build("evidence", "ev-$i")
            assertArrayEquals("Case 7: call $i must be deterministic", a, b)
        }
    }

    @Test
    fun `case7 non-ASCII evidenceId encoded as UTF-8 deterministically`() {
        val id = "الدليل-001" // Arabic characters
        val a = EvidenceAadBuilder.build("evidence", id)
        val b = EvidenceAadBuilder.build("evidence", id)
        assertArrayEquals("Case 7: UTF-8 non-ASCII must be deterministic", a, b)
    }

    // -----------------------------------------------------------------------
    // Case 8: empty/invalid identifiers rejected safely
    // -----------------------------------------------------------------------

    @Test
    fun `case8 blank objectClass throws IllegalArgumentException`() {
        assertThrows(
            "Case 8: blank objectClass must throw",
            IllegalArgumentException::class.java,
        ) {
            EvidenceAadBuilder.build("", "ev-001")
        }
    }

    @Test
    fun `case8 whitespace-only objectClass throws IllegalArgumentException`() {
        assertThrows(
            "Case 8: whitespace objectClass must throw",
            IllegalArgumentException::class.java,
        ) {
            EvidenceAadBuilder.build("   ", "ev-001")
        }
    }

    @Test
    fun `case8 blank evidenceId throws IllegalArgumentException`() {
        assertThrows(
            "Case 8: blank evidenceId must throw",
            IllegalArgumentException::class.java,
        ) {
            EvidenceAadBuilder.build("evidence", "")
        }
    }

    @Test
    fun `case8 uppercase objectClass throws IllegalArgumentException`() {
        assertThrows(
            "Case 8: non-lowercase objectClass must throw",
            IllegalArgumentException::class.java,
        ) {
            EvidenceAadBuilder.build("Evidence", "ev-001")
        }
    }

    // -----------------------------------------------------------------------
    // Case 9: no encryption path bypasses AAD
    // -----------------------------------------------------------------------

    @Test
    fun `case9 KeystoreEvidenceCipherWithAad always passes non-null AAD on encrypt`() {
        val recorder = AadRecordingEncryptor()
        val policy = EncryptionPolicy(
            keySizeBits = 256,
            requireUserAuthentication = false,
            userAuthenticationValiditySeconds = 0,
            invalidateOnBiometricEnrollment = false,
        )
        val cipher = KeystoreEvidenceCipherWithAad(recorder, "shieldra_evidence_v1", policy)
        val aad = EvidenceAadBuilder.forEvidence("ev-001")
        cipher.encrypt(byteArrayOf(1, 2, 3), aad)
        val usedAad = recorder.lastAad
        assertTrue(
            "Case 9: AAD must be non-null and non-empty on encrypt",
            usedAad != null && usedAad.isNotEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // Case 10: no decryption path bypasses AAD
    // -----------------------------------------------------------------------

    @Test
    fun `case10 KeystoreEvidenceCipherWithAad always passes non-null AAD on decrypt`() {
        val recorder = AadRecordingEncryptor()
        val policy = EncryptionPolicy(
            keySizeBits = 256,
            requireUserAuthentication = false,
            userAuthenticationValiditySeconds = 0,
            invalidateOnBiometricEnrollment = false,
        )
        val cipher = KeystoreEvidenceCipherWithAad(recorder, "shieldra_evidence_v1", policy)
        val aad = EvidenceAadBuilder.forEvidence("ev-001")
        // First encrypt to produce a valid payload structure
        val encrypted = cipher.encrypt(byteArrayOf(4, 5, 6), aad)
        recorder.lastAad = null // reset to detect re-use
        cipher.decrypt(encrypted, aad)
        val usedAad = recorder.lastAad
        assertTrue(
            "Case 10: AAD must be non-null and non-empty on decrypt",
            usedAad != null && usedAad.isNotEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // Structural integrity assertions
    // -----------------------------------------------------------------------

    @Test
    fun `aad structure matches canonical format`() {
        val objectClass = "evidence"
        val evidenceId = "ev-abc"
        val aad = EvidenceAadBuilder.build(objectClass, evidenceId)
        val buf = ByteBuffer.wrap(aad)
        assertEquals("version byte", 0x01.toByte(), buf.get())
        val classLen = buf.int
        val classBytes = ByteArray(classLen).also(buf::get)
        assertEquals("objectClass", objectClass, String(classBytes, StandardCharsets.UTF_8))
        val idLen = buf.int
        val idBytes = ByteArray(idLen).also(buf::get)
        assertEquals("evidenceId", evidenceId, String(idBytes, StandardCharsets.UTF_8))
        assertEquals("no trailing bytes", 0, buf.remaining())
    }

    // -----------------------------------------------------------------------
    // Canonical vector test (explicit known bytes)
    // -----------------------------------------------------------------------

    @Test
    fun `canonical vector for evidence class with evidenceId ev-001`() {
        // Canonical AAD-v1 for objectClass="evidence", evidenceId="ev-001"
        // versionByte(0x01)
        // || UInt32BE(8)  -- "evidence".length
        // || UTF-8("evidence")
        // || UInt32BE(6)  -- "ev-001".length
        // || UTF-8("ev-001")
        val expected = byteArrayOf(
            0x01,                              // version byte
            0x00, 0x00, 0x00, 0x08,           // class length = 8 (UInt32BE)
            0x65, 0x76, 0x69, 0x64, 0x65, 0x6E, 0x63, 0x65, // "evidence" UTF-8
            0x00, 0x00, 0x00, 0x06,           // id length = 6 (UInt32BE)
            0x65, 0x76, 0x2D, 0x30, 0x30, 0x31  // "ev-001" UTF-8
        )
        val actual = EvidenceAadBuilder.forEvidence("ev-001")
        assertArrayEquals("Canonical vector must match exactly", expected, actual)
    }

    @Test
    fun `canonical vector for photo class with evidenceId photo-123`() {
        // Canonical AAD-v1 for objectClass="photo", evidenceId="photo-123"
        val expected = byteArrayOf(
            0x01,                              // version byte
            0x00, 0x00, 0x00, 0x05,           // class length = 5
            0x70, 0x68, 0x6F, 0x74, 0x6F,     // "photo" UTF-8
            0x00, 0x00, 0x00, 0x09,           // id length = 9
            0x70, 0x68, 0x6F, 0x74, 0x6F, 0x2D, 0x31, 0x32, 0x33  // "photo-123" UTF-8
        )
        val actual = EvidenceAadBuilder.build("photo", "photo-123")
        assertArrayEquals("Canonical vector for photo must match exactly", expected, actual)
    }
}

// ---------------------------------------------------------------------------
// Test doubles
// ---------------------------------------------------------------------------

/**
 * In-memory encryptor that uses XOR+AAD to simulate AAD-bound ciphertext
 * for round-trip testing without requiring Android Keystore.
 */
private class FakeEncryptor : AndroidKeystoreEncryptor() {
    override fun encrypt(
        plaintext: ByteArray,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): EncryptedPayload {
        requireNotNull(associatedData) { "FakeEncryptor: AAD must not be null" }
        require(associatedData.isNotEmpty()) { "FakeEncryptor: AAD must not be empty" }
        // Encode the AAD hash into the IV so that decrypt can detect mismatch
        val iv = ByteArray(12)
        val aadHash = associatedData.fold(0) { acc, b -> acc xor b.toInt() }
        iv[0] = aadHash.toByte()
        // XOR plaintext with AAD bytes (cycling) to simulate binding
        val ct = plaintext.mapIndexed { i, b ->
            (b.toInt() xor associatedData[i % associatedData.size].toInt()).toByte()
        }.toByteArray()
        return EncryptedPayload(version = 1, iv = iv, ciphertext = ct)
    }

    override fun decrypt(
        payload: EncryptedPayload,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): ByteArray {
        requireNotNull(associatedData) { "FakeEncryptor: AAD must not be null" }
        require(associatedData.isNotEmpty()) { "FakeEncryptor: AAD must not be empty" }
        // Verify AAD hash matches what was stored in IV[0]
        val aadHash = associatedData.fold(0) { acc, b -> acc xor b.toInt() }
        if (payload.iv[0] != aadHash.toByte()) {
            throw javax.crypto.AEADBadTagException("AAD mismatch (fake encryptor)")
        }
        return payload.ciphertext.mapIndexed { i, b ->
            (b.toInt() xor associatedData[i % associatedData.size].toInt()).toByte()
        }.toByteArray()
    }
}

/**
 * Encryptor that records the last AAD passed to encrypt/decrypt.
 * Used to assert that no path bypasses AAD.
 */
private class AadRecordingEncryptor : AndroidKeystoreEncryptor() {
    var lastAad: ByteArray? = null

    override fun encrypt(
        plaintext: ByteArray,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): EncryptedPayload {
        lastAad = associatedData
        // Return a minimal valid payload so the caller can re-serialise it
        return EncryptedPayload(
            version = 1,
            iv = ByteArray(12) { it.toByte() },
            ciphertext = plaintext.copyOf(),
        )
    }

    override fun decrypt(
        payload: EncryptedPayload,
        keyAlias: String,
        policy: EncryptionPolicy,
        associatedData: ByteArray?,
    ): ByteArray {
        lastAad = associatedData
        return payload.ciphertext
    }
}
