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
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher)
        val plaintext = "minimum evidence".toByteArray()

        val file = store.write("evidence-1", plaintext)

        assertTrue(file.isFile)
        assertContentEquals(plaintext, store.read("evidence-1"))
        assertTrue(directory.list { _, name -> name.endsWith(".tmp") }.isNullOrEmpty())
    }

    @Test
    fun corrupted_payload_fails_closed() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher)
        store.write("evidence-1", "valid".toByteArray())
        directory.resolve("evidence-1.shieldra").writeBytes(byteArrayOf(0x7f))

        assertFailsWith<EvidenceUnavailableException> { store.read("evidence-1") }
    }

    @Test
    fun missing_payload_is_unavailable_and_path_traversal_is_rejected() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher)

        assertFailsWith<EvidenceUnavailableException> { store.read("missing") }
        assertFailsWith<IllegalArgumentException> { store.read("../outside") }
    }

    @Test
    fun orphan_scan_excludes_referenced_files_and_temporary_files() {
        val directory = Files.createTempDirectory("shieldra-evidence").toFile()
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher)
        store.write("kept", "one".toByteArray())
        store.write("orphan", "two".toByteArray())
        directory.resolve(".partial.tmp").writeBytes(byteArrayOf(1))

        assertTrue(store.orphanedEvidence(setOf("kept")) == setOf("orphan"))
    }

    private object PrefixCipher : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = byteArrayOf(0x01) + plaintext

        override fun decrypt(ciphertext: ByteArray): ByteArray {
            require(ciphertext.firstOrNull() == 0x01.toByte()) { "invalid authentication envelope" }
            return ciphertext.copyOfRange(1, ciphertext.size)
        }
    }
}
