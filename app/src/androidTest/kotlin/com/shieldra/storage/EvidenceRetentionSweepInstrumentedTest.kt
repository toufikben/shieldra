package com.shieldra.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.domain.EventId
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.storage.room.RoomEventRepository
import com.shieldra.storage.room.ShieldraDatabase
import java.io.File
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Q-K-2 on a real device: the approved windows must remove real rows and real
 * ciphertext, and must leave everything inside its window alone.
 *
 * The JVM plan tests prove the arithmetic; only a device run proves the two
 * halves agree, because a payload's age comes from the Room row that owns it and
 * the payload itself is a file. The fresh-timestamp case below is the one that
 * matters most: key rotation rewrites a payload and moves its file timestamp
 * forward, so a sweep that trusted the timestamp would let the 90-day rotation
 * interval renew a 30-day evidence window forever.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceRetentionSweepInstrumentedTest {
    private val day = 24L * 60L * 60L * 1_000L
    private val now = System.currentTimeMillis()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val expiredEventId: EventId = EventId.from(UUID.randomUUID())
    private val youngEventId: EventId = EventId.from(UUID.randomUUID())

    @Test
    fun expired_event_and_its_payload_are_removed_while_a_young_pair_survives() {
        val name = databaseName()
        val database = open(name)
        val directory = evidenceDirectory()
        val store = evidenceStore(directory)
        store.write(EXPIRED_EVIDENCE, "expired evidence".toByteArray())
        store.write(YOUNG_EVIDENCE, "young evidence".toByteArray())
        val repository = RoomEventRepository(database)
        // The expired payload was written moments ago, so its own file timestamp is
        // brand new. Only the owning event's creation stamp may expire it.
        assertEquals(
            RepositoryResult.Applied,
            repository.save(event(expiredEventId, EXPIRED_EVIDENCE, now - 91 * day)),
        )
        assertEquals(
            RepositoryResult.Applied,
            repository.save(event(youngEventId, YOUNG_EVIDENCE, now - day)),
        )

        val report = EvidenceRetentionSweeper(
            database = database,
            evidenceFiles = store,
            nowMillis = { now },
        ).sweep()

        assertEquals(1, report.deletedEventRows)
        assertEquals(setOf(EXPIRED_EVIDENCE), report.deletedEvidenceFileIds)
        assertEquals(emptySet<String>(), report.failedIds)
        assertFalse(payloadFile(directory, EXPIRED_EVIDENCE).isFile)
        assertTrue(payloadFile(directory, YOUNG_EVIDENCE).isFile)
        assertNull(repository.find(expiredEventId))
        assertNotNull(repository.find(youngEventId))
        assertEquals(0, database.evidenceReferenceDao().findAll().count { it.evidenceId == EXPIRED_EVIDENCE })
        database.close()
        delete(name, directory)
    }

    @Test
    fun quarantined_file_past_its_thirty_day_window_is_removed_and_a_young_one_is_kept() {
        val name = databaseName()
        val database = open(name)
        val directory = evidenceDirectory()
        val store = evidenceStore(directory)
        val quarantine = File(directory, QUARANTINE)
        assertTrue(quarantine.mkdirs())
        val expired = File(quarantine, "q-expired.shieldra").also { it.writeBytes(byteArrayOf(0x01)) }
        val kept = File(quarantine, "q-kept.shieldra").also { it.writeBytes(byteArrayOf(0x01)) }
        assertTrue(expired.setLastModified(now - 31 * day))
        assertTrue(kept.setLastModified(now - day))

        val report = EvidenceRetentionSweeper(
            database = database,
            evidenceFiles = store,
            nowMillis = { now },
        ).sweep()

        assertEquals(setOf("q-expired"), report.deletedQuarantineFileIds)
        assertFalse(expired.isFile)
        assertTrue(kept.isFile)
        assertEquals(0, report.deletedEventRows)
        database.close()
        delete(name, directory)
    }

    @Test
    fun nothing_expired_leaves_every_row_and_payload_in_place() {
        val name = databaseName()
        val database = open(name)
        val directory = evidenceDirectory()
        val store = evidenceStore(directory)
        store.write(YOUNG_EVIDENCE, "young evidence".toByteArray())
        // Inside the widest window, so no kind of stored thing has expired.
        RoomEventRepository(database).save(event(youngEventId, YOUNG_EVIDENCE, now - day))

        val report = EvidenceRetentionSweeper(
            database = database,
            evidenceFiles = store,
            nowMillis = { now },
        ).sweep()

        assertEquals(RetentionSweepReport(0, emptySet(), emptySet(), emptySet()), report)
        assertTrue(payloadFile(directory, YOUNG_EVIDENCE).isFile)
        assertEquals(1, database.securityEventDao().findAll().size)
        assertEquals(1, database.evidenceReferenceDao().findAll().size)
        database.close()
        delete(name, directory)
    }

    /**
     * The 30/30/90 asymmetry, measured rather than assumed: a payload expires with its owning
     * row's age under the 30-day evidence window, while the row itself serves 90. The payload
     * file is written seconds before the sweep, so only the row's stamp can be what deleted it.
     * The reference row is what the sweep leaves behind, and this asserts it because that is
     * what the approved windows do today: between day 30 and day 90 the record names a payload
     * that is no longer on the disk.
     */
    @Test
    fun payload_expires_at_its_thirty_day_window_while_the_row_that_owns_it_serves_ninety() {
        val name = databaseName()
        val database = open(name)
        val directory = evidenceDirectory()
        val store = evidenceStore(directory)
        store.write(YOUNG_EVIDENCE, "young evidence".toByteArray())
        val repository = RoomEventRepository(database)
        assertEquals(
            RepositoryResult.Applied,
            repository.save(event(youngEventId, YOUNG_EVIDENCE, now - 89 * day)),
        )

        val report = EvidenceRetentionSweeper(
            database = database,
            evidenceFiles = store,
            nowMillis = { now },
        ).sweep()

        assertEquals(0, report.deletedEventRows)
        assertEquals(setOf(YOUNG_EVIDENCE), report.deletedEvidenceFileIds)
        assertEquals(emptySet<String>(), report.failedIds)
        assertFalse(payloadFile(directory, YOUNG_EVIDENCE).isFile)
        assertNotNull(repository.find(youngEventId))
        assertEquals(1, database.evidenceReferenceDao().findAll().count { it.evidenceId == YOUNG_EVIDENCE })
        database.close()
        delete(name, directory)
    }

    private fun event(eventId: EventId, evidenceId: String, createdAtEpochMs: Long): SecurityEvent =
        SecurityEvent.create(
            eventId = eventId,
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
            evidenceReferences = listOf(EvidenceReference(evidenceId)),
        )

    private fun evidenceStore(directory: File): EncryptedEvidenceFileStore = EncryptedEvidenceFileStore(
        rootDirectory = directory,
        cipher = MarkerCipher,
        evidenceIdProvider = { EvidenceAadBuilder.forEvidence(it) },
    )

    private fun payloadFile(directory: File, evidenceId: String): File =
        File(directory, "$evidenceId.shieldra")

    private fun open(name: String): ShieldraDatabase = Room.databaseBuilder(
        context,
        ShieldraDatabase::class.java,
        name,
    ).build()

    private fun evidenceDirectory(): File {
        val directory = File(context.noBackupFilesDir, "retention-sweep-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs() || directory.isDirectory)
        return directory
    }

    private fun delete(name: String, directory: File) {
        context.deleteDatabase(name)
        directory.deleteRecursively()
    }

    private fun databaseName(): String = "retention-sweep-${UUID.randomUUID()}.db"

    private companion object {
        const val EXPIRED_EVIDENCE = "evidence-expired"
        const val YOUNG_EVIDENCE = "evidence-young"
        const val QUARANTINE = ".quarantine"
    }

    private object MarkerCipher : EvidenceCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
            require(aad.isNotEmpty()) { "MarkerCipher: AAD must not be empty" }
            return byteArrayOf(0x01) + plaintext
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            require(aad.isNotEmpty()) { "MarkerCipher: AAD must not be empty" }
            require(ciphertext.firstOrNull() == 0x01.toByte()) { "invalid authentication envelope" }
            return ciphertext.copyOfRange(1, ciphertext.size)
        }
    }
}
