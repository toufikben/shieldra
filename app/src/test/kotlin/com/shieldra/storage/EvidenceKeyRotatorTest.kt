package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verifies the resumable key rotation algorithm (decisions §3.3, §3.4)
 * against every required scenario of decision §3.5.
 *
 * All security boundaries are faked: [FakeCipher] models one Keystore key
 * generation with a tag so a wrong-key or wrong-AAD decrypt fails closed
 * exactly like AES-GCM authentication, and [FakeFiles]/[FakeMetadata]
 * model the evidence directory and the Room metadata rows.
 */
class EvidenceKeyRotatorTest {

    /** In-memory evidence directory. */
    private class FakeFiles : RotationFileGateway {
        val committed = mutableMapOf<String, ByteArray>()
        val temps = mutableMapOf<String, ByteArray>()

        override fun readCommitted(evidenceId: String): ByteArray =
            committed[evidenceId]?.copyOf()
                ?: throw EvidenceUnavailableException("missing committed evidence: $evidenceId")

        override fun hasTemp(evidenceId: String): Boolean = temps.containsKey(evidenceId)

        override fun writeTemp(evidenceId: String, payload: ByteArray) {
            temps[evidenceId] = payload.copyOf()
        }

        override fun readTemp(evidenceId: String): ByteArray =
            temps[evidenceId]?.copyOf()
                ?: throw EvidenceUnavailableException("missing rotation temp: $evidenceId")

        override fun commitTemp(evidenceId: String) {
            val payload = temps.remove(evidenceId)
                ?: throw IllegalStateException("no rotation temp to commit: $evidenceId")
            committed[evidenceId] = payload
        }

        override fun discardTemp(evidenceId: String) {
            temps.remove(evidenceId)
        }
    }

    /** Models one Keystore key generation: tag mismatch = wrong key, AAD mismatch = auth failure. */
    private class FakeCipher(private val tag: String) : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
            val tagBytes = tag.toByteArray(Charsets.UTF_8)
            return ByteArray(tagBytes.size + 1 + aad.size + 1 + plaintext.size).also { out ->
                var pos = 0
                tagBytes.copyInto(out, pos)
                pos += tagBytes.size
                out[pos++] = 0
                aad.copyInto(out, pos)
                pos += aad.size
                out[pos++] = 0
                plaintext.copyInto(out, pos)
            }
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            val tagBytes = tag.toByteArray(Charsets.UTF_8)
            if (ciphertext.size < tagBytes.size + 2 ||
                !ciphertext.copyOfRange(0, tagBytes.size).contentEquals(tagBytes)
            ) {
                throw EvidenceUnavailableException("ciphertext not bound to key '$tag'")
            }
            var pos = tagBytes.size
            if (ciphertext[pos++].toInt() != 0) throw EvidenceUnavailableException("corrupt payload framing")
            val aadEnd = ciphertext.indexOf(0.toByte(), pos)
            if (aadEnd < 0) throw EvidenceUnavailableException("corrupt payload framing")
            val storedAad = ciphertext.copyOfRange(pos, aadEnd)
            if (!storedAad.contentEquals(aad)) throw EvidenceUnavailableException("AAD mismatch for key '$tag'")
            return ciphertext.copyOfRange(aadEnd + 1, ciphertext.size)
        }
    }

    /** In-memory rotation metadata rows; [failNextMark] models one failed Room write. */
    private class FakeMetadata : RotationMetadataGateway {
        val rows = mutableMapOf<String, RotationMetadata>()
        var failNextMark = false

        override fun versionsOf(evidenceId: String): RotationMetadata? = rows[evidenceId]

        override fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String) {
            if (failNextMark) {
                failNextMark = false
                throw IllegalStateException("metadata write failed")
            }
            rows[evidenceId] = RotationMetadata(keyVersion, aadVersion)
        }
    }

    /** Registry of fake keys standing in for Keystore aliases. */
    private class FakeCiphers : RotationCipherFactory {
        val keys = mutableMapOf<String, FakeCipher>()

        fun register(keyVersion: String, cipher: FakeCipher) {
            keys[keyVersion] = cipher
        }

        override fun cipherFor(keyVersion: String): EvidenceCipher =
            keys[keyVersion] ?: throw IllegalArgumentException("no key registered for $keyVersion")
    }

    /** Deterministic ASCII AADs; evidence ids never contain a NUL byte. */
    private class FakeAad : RotationAadFactory {
        override fun aadFor(evidenceId: String, aadVersion: String): ByteArray =
            "$aadVersion|$evidenceId".toByteArray(Charsets.UTF_8)
    }

    private class Fixture {
        val files = FakeFiles()
        val metadata = FakeMetadata()
        val ciphers = FakeCiphers()
        val aad = FakeAad()
        val rotator = EvidenceKeyRotator(files, metadata, ciphers, aad)

        init {
            ciphers.register(EvidenceKeyPolicy.KEY_VERSION_V1, FakeCipher("keystore-key-v1"))
            ciphers.register(EvidenceKeyPolicy.KEY_VERSION_V2, FakeCipher("keystore-key-v2"))
        }

        fun request(id: String) = RotationRequest(
            evidenceId = id,
            fromKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
            toKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V2,
            fromAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
            toAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
        )

        /** Committed evidence encrypted under the v1 key with AAD-v1, metadata at v1. */
        fun seedV1(id: String, plaintext: String) {
            val v1 = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
            files.committed[id] = v1.encrypt(
                plaintext.toByteArray(Charsets.UTF_8),
                aad.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1),
            )
            metadata.rows[id] = RotationMetadata(
                EvidenceKeyPolicy.KEY_VERSION_V1,
                EvidenceKeyPolicy.AAD_VERSION_V1,
            )
        }

        fun decryptCommittedWithV2(id: String): String {
            val v2 = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
            val plain = v2.decrypt(
                files.committed.getValue(id),
                aad.aadFor(id, EvidenceKeyPolicy.AAD_VERSION_V1),
            )
            return plain.toString(Charsets.UTF_8)
        }

        fun metadataOf(id: String) = metadata.rows.getValue(id)

        fun migratedTarget() = RotationMetadata(
            EvidenceKeyPolicy.KEY_VERSION_V2,
            EvidenceKeyPolicy.AAD_VERSION_V1,
        )
    }

    @Test
    fun `normal rotation migrates evidence and marks metadata (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-001", "photo-bytes")
        assertEquals(RotationItemState.PENDING, fx.rotator.stateOf("ev-001"))

        val result = fx.rotator.rotate(fx.request("ev-001"))

        val migrated = assertIs<RotationResult.Migrated>(result)
        assertEquals("ev-001", migrated.evidenceId)
        assertTrue(!migrated.resumed, "normal rotation must not be reported as resumed")
        assertEquals("photo-bytes", fx.decryptCommittedWithV2("ev-001"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-001"))
        assertTrue(!fx.files.hasTemp("ev-001"), "no temp may survive a completed rotation")
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-001"))
        assertTrue(
            EvidenceKeyPolicy.KEY_VERSION_V1 in fx.ciphers.keys,
            "rotation must never delete the old key",
        )
    }

    @Test
    fun `interruption before encryption resumes and completes cleanly (decision 3-5)`() {
        val fx = Fixture()
        // A previous run died before the encrypt step: no temp exists and
        // the committed file is still v1. Re-running must simply complete.
        fx.seedV1("ev-002", "location-snapshot")

        val result = fx.rotator.rotate(fx.request("ev-002"))

        assertIs<RotationResult.Migrated>(result)
        assertEquals("location-snapshot", fx.decryptCommittedWithV2("ev-002"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-002"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-002"))
    }

    @Test
    fun `interruption after encryption commits leftover valid temp (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-003", "audio-clip")
        // A previous run encrypted under v2 and wrote the temp, then died.
        val v2 = fx.ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
        fx.files.temps["ev-003"] = v2.encrypt(
            "audio-clip".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-003", EvidenceKeyPolicy.AAD_VERSION_V1),
        )

        val result = fx.rotator.rotate(fx.request("ev-003"))

        val migrated = assertIs<RotationResult.Migrated>(result)
        assertTrue(migrated.resumed, "a valid leftover temp must be adopted as a resumed rotation")
        assertEquals("audio-clip", fx.decryptCommittedWithV2("ev-003"))
        assertTrue(!fx.files.hasTemp("ev-003"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-003"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-003"))
    }

    @Test
    fun `interruption before rename commits leftover verified temp (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-004", "sensor-log")
        // Same observable state as the post-encryption interruption (the
        // temp was written and verified but not yet renamed over the
        // committed file); decision §3.5 requires both named cases to
        // converge on the resumed commit path.
        val v2 = fx.ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
        fx.files.temps["ev-004"] = v2.encrypt(
            "sensor-log".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-004", EvidenceKeyPolicy.AAD_VERSION_V1),
        )

        val result = fx.rotator.rotate(fx.request("ev-004"))

        val migrated = assertIs<RotationResult.Migrated>(result)
        assertTrue(migrated.resumed)
        assertEquals("sensor-log", fx.decryptCommittedWithV2("ev-004"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-004"))
    }

    @Test
    fun `interruption after rename adopts committed file and marks metadata (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-005", "panic-photo")
        // A previous run renamed the v2 temp over the committed file but
        // died before marking metadata: committed is v2, metadata still v1.
        val v2 = fx.ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
        fx.files.committed["ev-005"] = v2.encrypt(
            "panic-photo".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-005", EvidenceKeyPolicy.AAD_VERSION_V1),
        )

        val result = fx.rotator.rotate(fx.request("ev-005"))

        val migrated = assertIs<RotationResult.Migrated>(result)
        assertTrue(migrated.resumed, "committed-but-unmarked evidence must be adopted, never re-encrypted")
        assertEquals("panic-photo", fx.decryptCommittedWithV2("ev-005"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-005"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-005"))
    }

    @Test
    fun `corrupted old evidence fails closed with evidence untouched (decision 3-5)`() {
        val fx = Fixture()
        fx.files.committed["ev-006"] = "####not-valid-evidence####".toByteArray(Charsets.UTF_8)
        fx.metadata.rows["ev-006"] = RotationMetadata(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            EvidenceKeyPolicy.AAD_VERSION_V1,
        )
        val original = fx.files.committed.getValue("ev-006").copyOf()

        val result = fx.rotator.rotate(fx.request("ev-006"))

        val failed = assertIs<RotationResult.Failed>(result)
        assertEquals("ev-006", failed.evidenceId)
        assertContentEquals(original, fx.files.committed.getValue("ev-006"), "failed rotation must not touch evidence")
        assertEquals(
            RotationMetadata(EvidenceKeyPolicy.KEY_VERSION_V1, EvidenceKeyPolicy.AAD_VERSION_V1),
            fx.metadataOf("ev-006"),
        )
        assertEquals(RotationItemState.FAILED, fx.rotator.stateOf("ev-006"))
    }

    @Test
    fun `corrupted new temp is discarded and rotation redone (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-007", "sim-change-proof")
        // Leftover temp the v2 key cannot authenticate (partial write).
        fx.files.temps["ev-007"] = "garbage-temp".toByteArray(Charsets.UTF_8)

        val result = fx.rotator.rotate(fx.request("ev-007"))

        val migrated = assertIs<RotationResult.Migrated>(result)
        assertTrue(
            !migrated.resumed,
            "an unverifiable temp must be discarded and rotation redone from scratch",
        )
        assertEquals("sim-change-proof", fx.decryptCommittedWithV2("ev-007"))
        assertTrue(!fx.files.hasTemp("ev-007"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-007"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-007"))
    }

    @Test
    fun `duplicate retry returns AlreadyMigrated without re-touching evidence (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-008", "battery-proof")
        assertIs<RotationResult.Migrated>(fx.rotator.rotate(fx.request("ev-008")))
        val afterFirst = fx.files.committed.getValue("ev-008").copyOf()

        val second = fx.rotator.rotate(fx.request("ev-008"))

        assertIs<RotationResult.AlreadyMigrated>(second)
        assertContentEquals(afterFirst, fx.files.committed.getValue("ev-008"), "duplicate retry must not re-encrypt")
        assertTrue(!fx.files.hasTemp("ev-008"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-008"))
    }

    @Test
    fun `already migrated evidence is skipped (decision 3-5)`() {
        val fx = Fixture()
        val v2 = fx.ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
        fx.files.committed["ev-009"] = v2.encrypt(
            "existing-v2-evidence".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-009", EvidenceKeyPolicy.AAD_VERSION_V1),
        )
        fx.metadata.rows["ev-009"] = fx.migratedTarget()
        val before = fx.files.committed.getValue("ev-009").copyOf()

        val result = fx.rotator.rotate(fx.request("ev-009"))

        assertIs<RotationResult.AlreadyMigrated>(result)
        assertContentEquals(before, fx.files.committed.getValue("ev-009"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-009"))
    }

    @Test
    fun `missing old key fails closed before touching evidence (decision 3-5)`() {
        val fx = Fixture()
        val orphanV1 = FakeCipher("keystore-key-v1")
        fx.files.committed["ev-010"] = orphanV1.encrypt(
            "old-proof".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-010", EvidenceKeyPolicy.AAD_VERSION_V1),
        )
        fx.metadata.rows["ev-010"] = RotationMetadata(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            EvidenceKeyPolicy.AAD_VERSION_V1,
        )
        // The v1 Keystore key is gone: the factory cannot resolve it.
        fx.ciphers.keys.remove(EvidenceKeyPolicy.KEY_VERSION_V1)
        val before = fx.files.committed.getValue("ev-010").copyOf()

        val result = fx.rotator.rotate(fx.request("ev-010"))

        val failed = assertIs<RotationResult.Failed>(result)
        assertTrue(
            "rotation endpoint unresolvable" in failed.reason,
            "missing old key must fail closed at endpoint resolution, got: ${failed.reason}",
        )
        assertContentEquals(before, fx.files.committed.getValue("ev-010"))
        assertEquals(
            RotationMetadata(EvidenceKeyPolicy.KEY_VERSION_V1, EvidenceKeyPolicy.AAD_VERSION_V1),
            fx.metadataOf("ev-010"),
        )
        assertEquals(RotationItemState.FAILED, fx.rotator.stateOf("ev-010"))
    }

    @Test
    fun `missing metadata fails closed (decision 3-5)`() {
        val fx = Fixture()
        fx.files.committed["ev-011"] = FakeCipher("keystore-key-v1").encrypt(
            "orphan-evidence".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-011", EvidenceKeyPolicy.AAD_VERSION_V1),
        )
        // No metadata row at all for this evidence.

        val result = fx.rotator.rotate(fx.request("ev-011"))

        val failed = assertIs<RotationResult.Failed>(result)
        assertEquals("rotation metadata missing", failed.reason)
        assertTrue(fx.files.committed.containsKey("ev-011"), "failed rotation must leave evidence in place")
        assertEquals(RotationItemState.FAILED, fx.rotator.stateOf("ev-011"))
    }

    @Test
    fun `mixed v1 and v2 evidence rotate independently (decision 3-5)`() {
        val fx = Fixture()
        fx.seedV1("ev-012a", "motion-frame")
        val v2 = fx.ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)
        fx.files.committed["ev-012b"] = v2.encrypt(
            "already-rotated".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-012b", EvidenceKeyPolicy.AAD_VERSION_V1),
        )
        fx.metadata.rows["ev-012b"] = fx.migratedTarget()
        val bBefore = fx.files.committed.getValue("ev-012b").copyOf()

        val results = fx.rotator.rotateAll(listOf("ev-012a", "ev-012b"), fx::request)

        assertIs<RotationResult.Migrated>(results.getValue("ev-012a"))
        assertIs<RotationResult.AlreadyMigrated>(results.getValue("ev-012b"))
        assertEquals("motion-frame", fx.decryptCommittedWithV2("ev-012a"))
        assertContentEquals(bBefore, fx.files.committed.getValue("ev-012b"), "v2 evidence must not be re-touched")
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-012a"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-012b"))
    }

    @Test
    fun `unexpected stored versions fail closed`() {
        val fx = Fixture()
        fx.files.committed["ev-013"] = FakeCipher("keystore-key-v1").encrypt(
            "x".toByteArray(Charsets.UTF_8),
            fx.aad.aadFor("ev-013", EvidenceKeyPolicy.AAD_VERSION_V1),
        )
        fx.metadata.rows["ev-013"] = RotationMetadata("v0", "AAD-v0")

        val result = fx.rotator.rotate(fx.request("ev-013"))

        val failed = assertIs<RotationResult.Failed>(result)
        assertTrue("unexpected stored versions" in failed.reason)
        assertEquals(
            RotationMetadata("v0", "AAD-v0"),
            fx.metadataOf("ev-013"),
            "mismatched metadata must not be modified",
        )
        assertEquals(RotationItemState.FAILED, fx.rotator.stateOf("ev-013"))
    }

    @Test
    fun `commit succeeded but mark failed is adoptable on retry`() {
        val fx = Fixture()
        fx.seedV1("ev-014", "screen-recording")
        fx.metadata.failNextMark = true

        val first = fx.rotator.rotate(fx.request("ev-014"))
        val failed = assertIs<RotationResult.Failed>(first)
        assertTrue("mark migrated failed" in failed.reason)
        // The committed file is already v2, but metadata still says v1.
        assertEquals("screen-recording", fx.decryptCommittedWithV2("ev-014"))
        assertEquals(
            RotationMetadata(EvidenceKeyPolicy.KEY_VERSION_V1, EvidenceKeyPolicy.AAD_VERSION_V1),
            fx.metadataOf("ev-014"),
        )
        assertEquals(RotationItemState.FAILED, fx.rotator.stateOf("ev-014"))

        val second = fx.rotator.rotate(fx.request("ev-014"))
        val migrated = assertIs<RotationResult.Migrated>(second)
        assertTrue(migrated.resumed, "a committed-but-unmarked file must be adopted on retry, never re-encrypted")
        assertEquals("screen-recording", fx.decryptCommittedWithV2("ev-014"))
        assertEquals(fx.migratedTarget(), fx.metadataOf("ev-014"))
        assertEquals(RotationItemState.VERIFIED, fx.rotator.stateOf("ev-014"))
    }
}
