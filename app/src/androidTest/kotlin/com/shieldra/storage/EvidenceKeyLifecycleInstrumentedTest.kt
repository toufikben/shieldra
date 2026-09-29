package com.shieldra.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.storage.room.EvidenceReferenceEntity
import com.shieldra.storage.room.RoomRotationMetadata
import com.shieldra.storage.room.SecurityEventEntity
import com.shieldra.storage.room.ShieldraDatabase
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof for the rotation trigger and the retirement record
 * (owner protocol §9/§10, decisions §3.1–§3.4).
 *
 * The JVM suites prove the algorithm and the record format. Only a device can
 * prove the three things this batch actually changes: that the eligibility scan
 * reads real Room rows, that the lifecycle record survives a reopen on the real
 * no-backup filesystem, and that a rotation triggered through production wiring
 * re-encrypts real Keystore evidence and then marks the old generation RETIRED
 * without ever destroying it (§10; destruction is the open decision Q-C8).
 */
@RunWith(AndroidJUnit4::class)
class EvidenceKeyLifecycleInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var evidenceDirectory: File
    private lateinit var lifecycleFile: File
    private lateinit var storage: LocalShieldraStorage

    @Before
    fun setUp() {
        databaseName = "lifecycle-${System.nanoTime()}.db"
        evidenceDirectory = File(context.noBackupFilesDir, "lifecycle_test_evidence")
        lifecycleFile = FileBackedKeyLifecycle.fileIn(context.noBackupFilesDir)
        evidenceDirectory.deleteRecursively()
        lifecycleFile.delete()
        storage = LocalShieldraStorage.create(
            context = context,
            databaseName = databaseName,
            evidenceDirectory = evidenceDirectory,
        )
    }

    @After
    fun tearDown() {
        storage.close()
        evidenceDirectory.deleteRecursively()
        lifecycleFile.delete()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun rotation_scope_lists_every_stored_evidence_version() {
        val database = storage.database
        insertEvent(database, "evt-scope")
        database.evidenceReferenceDao().insert(
            evidenceRow("ev-scope-a", "evt-scope", EvidenceKeyPolicy.KEY_VERSION_V1),
        )
        database.evidenceReferenceDao().insert(
            evidenceRow("ev-scope-b", "evt-scope", EvidenceKeyPolicy.KEY_VERSION_V2),
        )

        val scope: RotationScopeGateway = RoomRotationMetadata(database)
        val stored = scope.all()

        assertEquals(
            "the eligibility scan must see every stored evidence row",
            setOf("ev-scope-a", "ev-scope-b"),
            stored.keys,
        )
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, stored.getValue("ev-scope-a").keyVersion)
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, stored.getValue("ev-scope-b").keyVersion)
    }

    @Test
    fun the_key_lifecycle_record_is_durable_on_the_device_filesystem() {
        val clock = longArrayOf(1_700_000_000_000L)
        val writing = FileBackedKeyLifecycle(lifecycleFile) { clock[0] }
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V1, writing.snapshot().activeKeyVersion)

        writing.completeRotation(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            EvidenceKeyPolicy.KEY_VERSION_V2,
            atEpochMs = clock[0],
        )
        clock[0] = 1_800_000_000_000L
        val reopened = FileBackedKeyLifecycle(lifecycleFile) { clock[0] }.snapshot()

        assertTrue(
            "the record must live in the no-backup root, not inside the evidence directory",
            lifecycleFile.canonicalFile.path.startsWith(context.noBackupFilesDir.canonicalFile.path),
        )
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, reopened.activeKeyVersion)
        assertEquals(setOf(EvidenceKeyPolicy.KEY_VERSION_V1), reopened.retiredKeyVersions)
        assertEquals(
            "a relaunch must not reset the 90-day clock",
            1_700_000_000_000L,
            reopened.lastRotationEpochMs,
        )
    }

    @Test
    fun a_damaged_lifecycle_record_fails_closed_on_the_device() {
        val lifecycle = FileBackedKeyLifecycle(lifecycleFile) { 1_700_000_000_000L }
        lifecycle.snapshot()
        lifecycleFile.writeText("shieldra-key-lifecycle/1\nactive=v2\n")

        assertThrows(IllegalStateException::class.java) { lifecycle.snapshot() }
        assertEquals(
            "a failed read must not rewrite the record on top of itself",
            "shieldra-key-lifecycle/1\nactive=v2\n",
            lifecycleFile.readText(),
        )
    }

    @Test
    fun production_wiring_rotates_real_keystore_evidence_and_retires_the_old_generation() {
        val evidenceId = "ev-lifecycle-runtime-001"
        val plaintext = "device bound lifecycle payload".toByteArray(StandardCharsets.UTF_8)
        val aad = RotationAadVersions.aadFor(evidenceId, EvidenceKeyPolicy.AAD_VERSION_V1)
        val encryptor = AndroidKeystoreEncryptor(applicationContext = context)
        val ciphers = KeystoreRotationCipherFactory(encryptor)
        val v1 = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
        val v2 = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V2)

        // Warm both generations into existence before asserting on them: a fresh
        // install has no alias until the first use, and a missing alias is the
        // §6/A-4 recovery signal, not a "wrong key" signal.
        v2.encrypt("warm-up".toByteArray(StandardCharsets.UTF_8), RotationAadVersions.aadFor("$evidenceId-warm", EvidenceKeyPolicy.AAD_VERSION_V1))
        val files = FileSystemRotationGateway(evidenceDirectory)
        files.writeTemp(evidenceId, v1.encrypt(plaintext, aad))
        files.commitTemp(evidenceId)
        insertEvent(storage.database, "evt-lifecycle")
        storage.database.evidenceReferenceDao().insert(
            evidenceRow(evidenceId, "evt-lifecycle", EvidenceKeyPolicy.KEY_VERSION_V1),
        )

        val report = storage.rotateEvidenceIfNeeded(manual = true)

        assertEquals(
            "the manual trigger must rotate the whole set, got: $report",
            RotationTriggerOutcome.ROTATED,
            report.outcome,
        )
        assertEquals(RotationTrigger.MANUAL, report.trigger)
        assertEquals(listOf(evidenceId), report.migrated)
        assertEquals(
            "Room must record the new key version only after the file verified",
            EvidenceKeyPolicy.KEY_VERSION_V2,
            storage.database.evidenceReferenceDao().findById(evidenceId)?.keyVersion,
        )
        val record = FileBackedKeyLifecycle(lifecycleFile) { System.currentTimeMillis() }.snapshot()
        assertEquals(EvidenceKeyPolicy.KEY_VERSION_V2, record.activeKeyVersion)
        assertEquals(setOf(EvidenceKeyPolicy.KEY_VERSION_V1), record.retiredKeyVersions)

        val rotated = files.readCommitted(evidenceId)
        assertArrayEquals(
            "the rotated file must read back as the original plaintext under v2",
            plaintext,
            v2.decrypt(rotated, aad),
        )
        assertThrows(
            "the retired generation must no longer read the rotated evidence",
            EvidenceUnavailableException::class.java,
        ) { v1.decrypt(rotated, aad) }
        assertTrue(
            "rotation temps must not survive a completed run",
            evidenceDirectory.listFiles().orEmpty().none { it.name.endsWith(".tmp") },
        )
    }

    @Test
    fun a_run_that_is_not_due_touches_nothing() {
        val evidenceId = "ev-lifecycle-runtime-002"
        val aad = RotationAadVersions.aadFor(evidenceId, EvidenceKeyPolicy.AAD_VERSION_V1)
        val ciphers = KeystoreRotationCipherFactory(AndroidKeystoreEncryptor(applicationContext = context))
        val v1 = ciphers.cipherFor(EvidenceKeyPolicy.KEY_VERSION_V1)
        val files = FileSystemRotationGateway(evidenceDirectory)
        files.writeTemp(evidenceId, v1.encrypt("untouched".toByteArray(StandardCharsets.UTF_8), aad))
        files.commitTemp(evidenceId)
        insertEvent(storage.database, "evt-not-due")
        storage.database.evidenceReferenceDao().insert(
            evidenceRow(evidenceId, "evt-not-due", EvidenceKeyPolicy.KEY_VERSION_V1),
        )
        val before = files.readCommitted(evidenceId)

        val report = storage.rotateEvidenceIfNeeded(manual = false)

        assertEquals(RotationTriggerOutcome.NOT_DUE, report.outcome)
        assertTrue("a not-due run must migrate nothing", report.migrated.isEmpty())
        assertArrayEquals(
            "committed bytes must be identical when no rotation runs",
            before,
            files.readCommitted(evidenceId),
        )
        assertEquals(
            EvidenceKeyPolicy.KEY_VERSION_V1,
            storage.database.evidenceReferenceDao().findById(evidenceId)?.keyVersion,
        )
        assertEquals(
            "no rotation may retire a generation",
            EvidenceKeyPolicy.KEY_VERSION_V1,
            FileBackedKeyLifecycle(lifecycleFile) { System.currentTimeMillis() }.snapshot().activeKeyVersion,
        )
    }

    private fun insertEvent(database: ShieldraDatabase, eventId: String) {
        database.securityEventDao().insert(
            SecurityEventEntity(
                eventId = eventId,
                eventType = "PANIC",
                state = "INITIATED",
                severity = "HIGH",
                createdAtEpochMs = 1_700_000_000_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
                resumeStage = null,
                expiresAtEpochMs = null,
            ),
        )
    }

    private fun evidenceRow(
        evidenceId: String,
        eventId: String,
        keyVersion: String,
    ) = EvidenceReferenceEntity(
        evidenceId = evidenceId,
        eventId = eventId,
        kind = "PHOTO",
        contentReference = "files/$evidenceId",
        capturedAtEpochMs = 1_700_000_000_000L,
        keyVersion = keyVersion,
        aadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
    )
}
