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

    private fun metadataStore(): File = File(System.getProperty("java.io.tmpdir"), "shieldra-reset-meta")
        .apply { deleteRecursively(); mkdirs() }

    @Test
    fun reset_removes_files_metadata_and_the_keystore_key() {
        val dir = evidenceDir()
        val meta = metadataStore()
        File(dir, "ev-1.shieldra").writeBytes("evidence".toByteArray())
        File(meta, "events.db").writeBytes("rows".toByteArray())
        val deletedKeys = mutableListOf<String>()
        val reset = FactoryReset(
            evidenceDirectory = dir,
            metadataDatabase = meta,
            keyDeletion = { alias -> deletedKeys += alias; true },
        )

        val result = reset.execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2),
        )

        assertIs<FactoryResetResult.Completed>(result)
        assertEquals(
            listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2),
            deletedKeys,
            "A-2: reset deletes the evidence key, including the rotation target",
        )
        assertFalse(File(dir, "ev-1.shieldra").exists(), "evidence files are removed")
        assertFalse(File(meta, "events.db").exists(), "room metadata is removed")
    }

    @Test
    fun reset_is_idempotent_when_storage_is_already_empty() {
        val reset = FactoryReset(
            evidenceDirectory = evidenceDir(),
            metadataDatabase = metadataStore(),
            keyDeletion = { true },
        )

        val first = reset.execute(aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS))
        val second = reset.execute(aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS))

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

        // A file the reset cannot reach: its parent directory does not exist,
        // so the post-removal absence check has something real to fail on
        // instead of a simulated error.
        val unreachable = File(dir, "no-such-dir").resolve("nested/ev-2.shieldra")
        val result = reset.execute(
            aliases = listOf(EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS),
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
