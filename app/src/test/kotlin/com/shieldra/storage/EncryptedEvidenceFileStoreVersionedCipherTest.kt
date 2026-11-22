package com.shieldra.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The read path must follow the key version stored with each evidence row, and the write path
 * the active generation: one fixed v1 cipher made rotated evidence unreadable.
 */
class EncryptedEvidenceFileStoreVersionedCipherTest {

    /** Deterministic fake: ciphertext = "<tag>|<aad>|<plaintext>"; decrypt requires its own tag. */
    private class TaggedCipher(private val tag: String) : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray =
            "$tag|".toByteArray() + aad + "|".toByteArray() + plaintext

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            val text = ciphertext.toString(Charsets.UTF_8)
            check(text.startsWith("$tag|")) { "wrong key generation" }
            return text.substringAfter("$tag|").substringAfter("|").toByteArray()
        }
    }

    private class FakeResolver(
        var active: String,
        val versions: MutableMap<String, String>,
    ) : EvidenceCipherResolver {
        override fun writeCipher(): EvidenceCipher = TaggedCipher(active)

        override fun readCipher(evidenceId: String): EvidenceCipher {
            val version = versions[evidenceId]
                ?: throw EvidenceUnavailableException("no stored key version for $evidenceId")
            return TaggedCipher(version)
        }
    }

    private fun store(directory: java.io.File, resolver: EvidenceCipherResolver) = EncryptedEvidenceFileStore(
        directory,
        TaggedCipher("legacy"),
        { EvidenceAadBuilder.forEvidence(it) },
        resolver,
    )

    @Test
    fun `write uses the active generation and read uses the stored generation`() {
        val directory = Files.createTempDirectory("shieldra-versioned").toFile()
        val resolver = FakeResolver(active = "v1", versions = mutableMapOf())
        val store = store(directory, resolver)

        store.write("evidence-1", "first".toByteArray())
        resolver.versions["evidence-1"] = "v1"
        assertContentEquals("first".toByteArray(), store.read("evidence-1"))
        assertTrue(directory.resolve("evidence-1.shieldra").readText().startsWith("v1|"))

        // Rotation completes: new writes are sealed with v2, old rows stay readable via v1.
        resolver.active = "v2"
        store.write("evidence-2", "second".toByteArray())
        resolver.versions["evidence-2"] = "v2"
        assertTrue(directory.resolve("evidence-2.shieldra").readText().startsWith("v2|"))
        assertContentEquals("second".toByteArray(), store.read("evidence-2"))
        assertContentEquals("first".toByteArray(), store.read("evidence-1"))
    }

    @Test
    fun `missing stored version fails closed`() {
        val directory = Files.createTempDirectory("shieldra-versioned").toFile()
        val resolver = FakeResolver(active = "v1", versions = mutableMapOf())
        val store = store(directory, resolver)
        store.write("evidence-1", "first".toByteArray())

        assertFailsWith<EvidenceUnavailableException> { store.read("evidence-1") }
    }

    @Test
    fun `a row marked migrated is read with the new generation`() {
        val directory = Files.createTempDirectory("shieldra-versioned").toFile()
        val resolver = FakeResolver(active = "v2", versions = mutableMapOf())
        val store = store(directory, resolver)
        store.write("evidence-1", "payload".toByteArray())
        resolver.versions["evidence-1"] = "v2"
        assertContentEquals("payload".toByteArray(), store.read("evidence-1"))
    }
}
