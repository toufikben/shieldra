package com.shieldra.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Durable half of the retirement lifecycle (owner protocol §9/§10, decisions §3.1–§3.4).
 *
 * The orchestrator decides *whether* to rotate; this record says which key
 * generation is active, which ones are RETIRED, and when the last rotation
 * completed. It is the only place the 90-day clock is kept, so these tests pin
 * the three properties the protocol asks for: the clock survives a relaunch, a
 * damaged record fails closed instead of silently reopening rotation on an
 * assumed version, and nothing here can destroy a key.
 */
class FileBackedKeyLifecycleTest {

    private fun directory(label: String): File =
        Files.createTempDirectory("shieldra-$label").toFile()

    private class Clock(var now: Long = 1_700_000_000_000L) {
        val millis: () -> Long get() = { now }

        fun advance(days: Long) {
            now += days * 24L * 60L * 60L * 1000L
        }
    }

    private fun record(label: String): File = File(directory(label), "key-lifecycle.bin")

    @Test
    fun a_first_use_opens_the_installed_key_generation_at_the_creation_time() {
        val file = record("lifecycle-create")
        val clock = Clock()

        val snapshot = FileBackedKeyLifecycle(file, clock.millis).snapshot()

        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, snapshot.activeKeyVersion)
        assertTrue(snapshot.retiredKeyVersions.isEmpty(), "a fresh install has no retired generation")
        assertEquals(clock.now, snapshot.lastRotationEpochMs, "the interval clock starts at creation")
        assertTrue(file.isFile, "the record is persisted so the baseline is not re-invented")
    }

    @Test
    fun the_creation_baseline_is_not_reset_by_a_later_launch() {
        val file = record("lifecycle-relaunch")
        val firstLaunch = Clock()
        val original = FileBackedKeyLifecycle(file, firstLaunch.millis).snapshot()
        val laterLaunch = Clock().apply { advance(30) }

        val reopened = FileBackedKeyLifecycle(file, laterLaunch.millis).snapshot()

        assertEquals(original.lastRotationEpochMs, reopened.lastRotationEpochMs)
        assertEquals(original.activeKeyVersion, reopened.activeKeyVersion)
    }

    @Test
    fun completing_a_rotation_is_durable_and_marks_the_old_version_retired() {
        val file = record("lifecycle-rotate")
        val clock = Clock()
        FileBackedKeyLifecycle(file, clock.millis).completeRotation(
            fromKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V1,
            toKeyVersion = EvidenceKeyPolicy.KEY_VERSION_V2,
            atEpochMs = clock.now,
        )

        val after = FileBackedKeyLifecycle(file, Clock(clock.now + 60_000).millis).snapshot()

        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, after.activeKeyVersion)
        assertEquals(setOf(EvidenceKeyPolicy.KEY_VERSION_V1), after.retiredKeyVersions)
        assertEquals(clock.now, after.lastRotationEpochMs)
    }

    @Test
    fun only_the_installed_generation_has_an_approved_successor() {
        val lifecycle = FileBackedKeyLifecycle(record("lifecycle-successor"), Clock().millis)

        assertEquals(
            EvidenceKeyPolicy.KEY_VERSION_V2,
            lifecycle.nextKeyVersion(EvidenceKeyPolicy.KEY_VERSION_V1),
        )
        assertEquals(
            null,
            lifecycle.nextKeyVersion(EvidenceKeyPolicy.KEY_VERSION_V2),
            "no v3 alias is approved (decisions 3.1); rotation must stop instead of inventing one",
        )
        assertEquals(null, lifecycle.nextKeyVersion("v-unapproved"))
    }

    @Test
    fun retiring_a_version_that_is_not_active_is_rejected_and_the_record_is_untouched() {
        val file = record("lifecycle-guard")
        val clock = Clock()
        val lifecycle = FileBackedKeyLifecycle(file, clock.millis)
        lifecycle.snapshot()

        assertFailsWith<IllegalStateException> {
            lifecycle.completeRotation("v-something-else", EvidenceKeyPolicy.KEY_VERSION_V2, clock.now)
        }

        val unchanged = FileBackedKeyLifecycle(file, clock.millis).snapshot()
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, unchanged.activeKeyVersion)
        assertTrue(unchanged.retiredKeyVersions.isEmpty())
    }

    @Test
    fun a_negative_completion_timestamp_is_rejected() {
        val lifecycle = FileBackedKeyLifecycle(record("lifecycle-time"), Clock().millis)
        lifecycle.snapshot()

        assertFailsWith<IllegalArgumentException> {
            lifecycle.completeRotation(
                EvidenceKeyPolicy.KEY_VERSION_V1,
                EvidenceKeyPolicy.KEY_VERSION_V2,
                atEpochMs = -1L,
            )
        }
    }

    @Test
    fun a_damaged_record_fails_closed_instead_of_assuming_a_version() {
        val file = record("lifecycle-corrupt")
        val clock = Clock()
        FileBackedKeyLifecycle(file, clock.millis).snapshot()
        FileBackedKeyLifecycle(file, clock.millis).completeRotation(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            EvidenceKeyPolicy.KEY_VERSION_V2,
            clock.now,
        )
        val healthy = file.readText()
        file.writeText(healthy.substring(0, healthy.length / 2))

        val error = assertFailsWith<IllegalStateException> {
            FileBackedKeyLifecycle(file, clock.millis).snapshot()
        }
        assertTrue(
            error.message.orEmpty().contains("lifecycle"),
            "the failure must name the lifecycle record so the caller can report it: ${error.message}",
        )
    }

    @Test
    fun a_record_written_by_an_unknown_schema_version_fails_closed() {
        val file = record("lifecycle-schema")
        file.writeText("shieldra-key-lifecycle/999\nactive=v1\n")

        assertFailsWith<IllegalStateException> {
            FileBackedKeyLifecycle(file, Clock().millis).snapshot()
        }
    }

    @Test
    fun a_completed_write_leaves_no_partial_file_beside_the_record() {
        val storageDirectory = directory("lifecycle-atomic")
        val file = File(storageDirectory, "key-lifecycle.bin")
        val clock = Clock()
        FileBackedKeyLifecycle(file, clock.millis).snapshot()

        FileBackedKeyLifecycle(file, clock.millis).completeRotation(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            EvidenceKeyPolicy.KEY_VERSION_V2,
            clock.now,
        )

        val siblings = storageDirectory.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("key-lifecycle.bin"), siblings, "rotation temps must not linger next to the record")
    }

    @Test
    fun the_record_lives_outside_the_encrypted_evidence_directory() {
        val storageRoot = directory("lifecycle-place")
        val evidenceDirectory = File(storageRoot, "shieldra-evidence").apply { mkdirs() }

        val file = FileBackedKeyLifecycle.fileIn(storageRoot)

        assertEquals(storageRoot.canonicalFile, file.parentFile?.canonicalFile)
        assertTrue(
            !file.canonicalFile.path.startsWith(evidenceDirectory.canonicalFile.path + File.separator),
            "reconcile sweeps temporary files inside the evidence directory; the record must stay out of it",
        )
    }

    @Test
    fun the_durable_lifecycle_exposes_no_key_destruction_surface() {
        val members = FileBackedKeyLifecycle::class.java.declaredMethods.map { it.name.lowercase() }

        assertTrue(
            members.none { name ->
                name.contains("delete") || name.contains("destroy") || name.contains("remove") || name.contains("erase")
            },
            "retirement is a state transition, never a key deletion (Q-C8 is an open owner decision): $members",
        )
    }
}
