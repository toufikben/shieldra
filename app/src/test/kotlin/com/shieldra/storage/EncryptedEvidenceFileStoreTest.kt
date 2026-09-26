package com.shieldra.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EncryptedEvidenceFileStoreTest {
    @Test
    fun writes_and_reads_only_after_atomic_replacement() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        val plaintext = "minimum evidence".toByteArray()

        val file = store.write("evidence-1", plaintext)

        assertTrue(file.isFile)
        assertContentEquals(plaintext, store.read("evidence-1"))
        assertTrue(directory.list { _, name -> name.endsWith(".tmp") }.isNullOrEmpty())
    }

    @Test
    fun corrupted_payload_fails_closed() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        store.write("evidence-1", "valid".toByteArray())
        directory.resolve("evidence-1.shieldra").writeBytes(byteArrayOf(0x7f))

        assertFailsWith<EvidenceUnavailableException> { store.read("evidence-1") }
    }

    @Test
    fun symlink_payload_is_not_read_as_evidence() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val outside = Files.createTempFile("shieldra-outside", ".payload")
        outside.toFile().writeBytes(byteArrayOf(0x01, 0x73))
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        Files.createSymbolicLink(
            directory.resolve("evidence-1.shieldra").toPath(),
            outside,
        )

        assertFailsWith<EvidenceUnavailableException> { store.read("evidence-1") }
    }

    @Test
    fun missing_payload_is_unavailable_and_path_traversal_is_rejected() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)

        assertFailsWith<EvidenceUnavailableException> { store.read("missing") }
        assertFailsWith<IllegalArgumentException> { store.read("../outside") }
    }

    @Test
    fun delete_reports_failure_instead_of_ignoring_it() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        val nonEmptyDirectory = directory.resolve("blocked.shieldra")
        assertTrue(nonEmptyDirectory.mkdirs())
        nonEmptyDirectory.resolve("child").writeBytes(byteArrayOf(1))

        assertFailsWith<EvidenceUnavailableException> { store.delete("blocked") }
        assertTrue(nonEmptyDirectory.isDirectory)
    }

    @Test
    fun reconciliation_does_not_replace_an_existing_quarantine_file() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        store.write("orphan", "source".toByteArray())
        val existingQuarantine = directory.resolve(".quarantine/orphan.shieldra")
        assertTrue(existingQuarantine.parentFile.mkdirs())
        val previousQuarantine = "previous quarantine".toByteArray()
        existingQuarantine.writeBytes(previousQuarantine)

        assertFailsWith<EvidenceUnavailableException> {
            store.reconcile(emptySet())
        }

        assertTrue(directory.resolve("orphan.shieldra").isFile)
        assertContentEquals(previousQuarantine, existingQuarantine.readBytes())
    }

    @Test
    fun orphan_scan_excludes_referenced_files_and_temporary_files() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val aadProvider: (String) -> ByteArray = { EvidenceAadBuilder.forEvidence(it) }
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher, aadProvider)
        store.write("kept", "one".toByteArray())
        store.write("orphan", "two".toByteArray())
        directory.resolve(".partial.tmp").writeBytes(byteArrayOf(1))

        assertTrue(store.orphanedEvidence(setOf("kept")) == setOf("orphan"))
    }

    private object PrefixCipher : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
            require(aad.isNotEmpty()) { "PrefixCipher: AAD must not be empty" }
            return byteArrayOf(0x01) + plaintext
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            require(aad.isNotEmpty()) { "PrefixCipher: AAD must not be empty" }
            require(ciphertext.firstOrNull() == 0x01.toByte()) { "invalid authentication envelope" }
            return ciphertext.copyOfRange(1, ciphertext.size)
        }
    }
}