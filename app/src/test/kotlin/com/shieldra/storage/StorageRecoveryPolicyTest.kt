package com.shieldra.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageRecoveryPolicyTest {
    @Test
    fun reconciliation_reports_missing_and_quarantines_orphaned_files() {
        val directory = Files.createTempDirectory("shieldra-reconcile").toFile()
        val store = EncryptedEvidenceFileStore(directory, PrefixCipher)
        store.write("referenced", "one".toByteArray())
        store.write("orphan", "two".toByteArray())
        directory.resolve(".interrupted.tmp").writeBytes(byteArrayOf(1))

        val report = store.reconcile(setOf("referenced", "missing"))

        assertEquals(setOf("missing"), report.missingReferencedIds)
        assertEquals(setOf("orphan"), report.orphanedFileIds)
        assertEquals(setOf("orphan"), report.quarantinedFileIds)
        assertEquals(setOf(".interrupted.tmp"), report.deletedTemporaryFiles)
        assertTrue(directory.resolve(".quarantine/orphan.shieldra").isFile)
        assertTrue(!directory.resolve("orphan.shieldra").exists())
    }

    @Test
    fun migration_policy_rejects_downgrade_and_destructive_fallback() {
        assertFailsWith<IllegalArgumentException> {
            StorageMigrationPolicy.requireForwardMigration(2, 1)
        }
        assertFailsWith<IllegalStateException> {
            StorageMigrationPolicy.requireNoDestructiveFallback(true)
        }
        StorageMigrationPolicy.requireForwardMigration(1, 2)
        StorageMigrationPolicy.requireNoDestructiveFallback(false)
    }

    @Test
    fun unsupported_schema_versions_fail_closed() {
        assertFailsWith<IllegalArgumentException> {
            requireSupportedSchemaVersion(0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireSupportedSchemaVersion(StorageSchema.VERSION + 1)
        }
    }

    private object PrefixCipher : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = byteArrayOf(0x01) + plaintext

        override fun decrypt(ciphertext: ByteArray): ByteArray {
            require(ciphertext.firstOrNull() == 0x01.toByte())
            return ciphertext.copyOfRange(1, ciphertext.size)
        }
    }
}
