package com.shieldra.storage

import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Factory reset (decision §5.7, amendments A-2 and A-3).
 *
 * A-2 makes key deletion mandatory; A-3 makes removal precede verification and
 * requires a failure to be tombstoned rather than reported as success.
 */
class FactoryResetTest {

    private fun evidenceDir(): File = File(System.getProperty("java.io.tmpdir"), "shieldra-reset-test")
        .apply { deleteRecursively(); mkdirs() }

    // The Room database is a single file, so the reset's metadata target is
    // the file itself, not a directory — a non-empty directory cannot be
    // removed by Files.deleteIfExists and would correctly tombstone the reset.
    private fun metadataStore(): File =
        File(System.getProperty("java.io.tmpdir"), "shieldra-reset-meta.db")
            .apply { delete(); parentFile?.mkdirs() }

    @Test
    fun reset_removes_files_metadata_and_the_keystore_key() {
        val dir = evidenceDir()
        val meta = metadataStore()
        File(dir, "ev-1.shieldra").writeBytes("evidence".toByteArray())
        meta.writeBytes("rows".toByteArray())
        val deletedKeys = mutableListOf<String>()
        val reset = FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = meta,
            keyDeletion = { alias -> deletedKeys += alias; true },
        )

        val result = reset.execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2),
            confirmed = true,
        )

        assertIs<FactoryResetResult.Completed>(result)
        assertEquals(
            listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2),
            deletedKeys,
            "A-2: reset deletes the evidence key, including the rotation target",
        )
        assertFalse(File(dir, "ev-1.shieldra").exists(), "evidence files are removed")
        assertFalse(meta.exists(), "room metadata is removed")
    }

    @Test
    fun reset_empties_the_store_s_own_quarantine_and_the_database_wal_and_shm_files() {
        val dir = evidenceDir()
        val meta = metadataStore()
        val quarantine = File(dir, EncryptedEvidenceFileStore.QUARANTINE_DIRECTORY).apply { mkdirs() }
        val quarantined = File(quarantine, "ev-q.shieldra").apply { writeBytes("ciphertext".toByteArray()) }
        meta.writeBytes("rows".toByteArray())
        val wal = File(meta.path + "-wal").apply { writeBytes("recent rows".toByteArray()) }
        val shm = File(meta.path + "-shm").apply { writeBytes("index".toByteArray()) }

        val result = FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = meta,
            keyDeletion = { true },
        ).execute(aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS), confirmed = true)

        assertIs<FactoryResetResult.Completed>(result)
        assertFalse(quarantined.exists(), "§5.2: quarantined ciphertext must not outlive a reset")
        assertFalse(wal.exists(), "rows still in the write-ahead log must not outlive a reset")
        assertFalse(shm.exists(), "the WAL index must not outlive a reset")
    }

    @Test
    fun reset_is_idempotent_when_storage_is_already_empty() {
        val reset = FactoryReset(
            evidenceDirectory = evidenceDir(),
            metadataDatabase = metadataStore(),
            keyDeletion = { true },
        )

        val first = reset.execute(aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS), confirmed = true)
        val second = reset.execute(aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS), confirmed = true)

        assertIs<FactoryResetResult.Completed>(first)
        assertIs<FactoryResetResult.Completed>(second)
    }

    @Test
    fun a_failed_cleanup_is_tombstoned_and_never_reported_as_success() {
        val dir = evidenceDir()
        val meta = metadataStore()
        val deletedKeys = mutableListOf<String>()
        val reset = FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = meta,
            keyDeletion = { alias -> deletedKeys += alias; true },
        )

        // A file the reset must account for but structurally cannot reach:
        // it lives in a scratch directory that is not the evidence directory,
        // so the removal loop never sees it and the post-removal absence check
        // fails for a real reason rather than a simulated one. This is what a
        // caller passes when it tracks evidence paths from metadata rather
        // than from a single directory.
        val trackedElsewhere = File(System.getProperty("java.io.tmpdir"), "shieldra-reset-tracked")
            .apply { deleteRecursively(); mkdirs() }
        val unreachable = File(trackedElsewhere, "ev-2.shieldra").apply { writeBytes("evidence".toByteArray()) }
        val result = reset.execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS),
            confirmed = true,
            survivingFiles = listOf(unreachable),
        )

        val tombstoned = assertIs<FactoryResetResult.Tombstoned>(result)
        assertTrue(
            tombstoned.failures.any { "ev-2.shieldra" in it },
            "the tombstone names the file that survived: ${tombstoned.failures}",
        )
        assertTrue(
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS in deletedKeys,
            "A-3: removal precedes verification, so the key is deleted even when cleanup failed",
        )
    }

    @Test
    fun reset_refuses_to_run_without_an_explicit_destructive_confirmation() {
        val dir = evidenceDir()
        File(dir, "ev-1.shieldra").writeBytes("evidence".toByteArray())
        val deletedKeys = mutableListOf<String>()
        val reset = FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = metadataStore(),
            keyDeletion = { alias -> deletedKeys += alias; true },
        )

        val result = reset.execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS),
            confirmed = false,
        )

        assertIs<FactoryResetResult.Refused>(result)
        assertTrue(File(dir, "ev-1.shieldra").exists(), "nothing may be deleted without confirmation (§5.7)")
        assertTrue(deletedKeys.isEmpty(), "no key may be deleted without confirmation (§5.7)")
    }

    @Test
    fun reset_never_runs_on_its_own() {
        val dir = evidenceDir()
        File(dir, "ev-1.shieldra").writeText("keep", StandardCharsets.UTF_8)

        // Constructing the reset object and never calling execute must not
        // delete anything: §5.6 forbids silent deletion on startup.
        FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = metadataStore(),
            keyDeletion = { true },
        )

        assertTrue(File(dir, "ev-1.shieldra").exists())
    }

    @Test
    fun an_empty_alias_list_is_rejected() {
        val reset = FactoryReset(
            evidenceDirectory = evidenceDir(),
            metadataDatabase = metadataStore(),
            keyDeletion = { true },
        )

        val result = reset.execute(aliases = emptyList(), confirmed = true)

        assertIs<FactoryResetResult.Refused>(result)
    }
}
