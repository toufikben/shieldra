package com.shieldra.storage.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.DeliveryStage
import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.EvidenceStage
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.InMemoryEventPipeline
import com.shieldra.domain.PipelineResult
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.StageOutcome
import com.shieldra.storage.EvidenceCipher
import com.shieldra.storage.LocalShieldraStorage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomEventRepositoryInstrumentedTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-09-23T05:00:00Z"),
        ZoneOffset.UTC,
    )
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun duplicate_event_id_is_idempotent_and_survives_database_reopen() {
        val name = databaseName()
        var database = open(name)
        val event = event()
        val repository = RoomEventRepository(database, fixedClock)

        assertEquals(RepositoryResult.Applied, repository.save(event))
        assertEquals(RepositoryResult.AlreadyExists, repository.save(event))
        database.close()

        database = open(name)
        val reopened = RoomEventRepository(database, fixedClock)
        assertNotNull(reopened.find(event.id))
        assertEquals(EventState.INITIATED, reopened.find(event.id)?.state)
        database.close()
        delete(name)
    }

    @Test
    fun expiration_is_preserved_by_room_mapping_and_database_reopen() {
        val name = databaseName()
        val expiration = Instant.parse("2026-09-23T20:00:00Z")
        var database = open(name)
        val original = event()
        val expiringEvent = SecurityEvent.create(
            eventId = original.id,
            createdAt = original.createdAt,
            expiresAt = expiration,
        )
        RoomEventRepository(database, fixedClock).save(expiringEvent)
        database.close()

        database = open(name)
        val reopened = RoomEventRepository(database, fixedClock).find(expiringEvent.id)
        assertEquals(expiration, reopened?.expiresAt)
        database.close()
        delete(name)
    }

    @Test
    fun state_update_is_atomic_and_wrong_expected_state_conflicts() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val event = event()
        repository.save(event)

        assertEquals(
            RepositoryResult.Applied,
            repository.updateState(event.id, EventState.INITIATED, EventState.COLLECTING),
        )
        assertEquals(
            RepositoryResult.Conflict(EventState.COLLECTING),
            repository.updateState(event.id, EventState.INITIATED, EventState.FAILED_FINAL),
        )
        assertEquals(EventState.COLLECTING, repository.find(event.id)?.state)
        database.close()
        delete(name)
    }

    @Test
    fun deferred_pipeline_resumes_from_persisted_delivery_stage_after_reopen() {
        val name = databaseName()
        var database = open(name)
        val event = event()
        val repository = RoomEventRepository(database, fixedClock)
        repository.save(event)
        var attempts = 0
        val firstPipeline = InMemoryEventPipeline(
            repository = repository,
            evidence = recordingEvidence(repository),
            delivery = DeliveryStage {
                attempts += 1
                StageOutcome.Deferred
            },
            now = { fixedClock.instant() },
        )

        firstPipeline.process(event.id)
        firstPipeline.process(event.id)
        val deferred = firstPipeline.process(event.id)
        assertTrue("A deferred delivery must be reported as deferred", deferred is PipelineResult.Deferred)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)
        database.close()

        database = open(name)
        val reopenedRepository = RoomEventRepository(database, fixedClock)
        val resumed = InMemoryEventPipeline(
            repository = reopenedRepository,
            evidence = recordingEvidence(reopenedRepository),
            delivery = DeliveryStage { attempt ->
                attempts += 1
                reopenedRepository.recordDeliveryAttempt(
                    attempt.id,
                    DeliveryInformation(
                        channel = "device-test",
                        status = DeliveryStatus.SUCCESS,
                        occurredAt = attempt.createdAt,
                    ),
                )
                StageOutcome.Completed
            },
            now = { fixedClock.instant() },
        ).process(event.id)

        assertTrue("A resumed delivery that really sent must reach delivered", resumed is PipelineResult.Advanced)
        assertEquals(EventState.DELIVERED, reopenedRepository.find(event.id)?.state)
        assertEquals(2, attempts)
        database.close()
        delete(name)
    }

    @Test
    fun recorded_evidence_and_delivery_attempt_survive_a_reopen() {
        val name = databaseName()
        var database = open(name)
        val event = event()
        val repository = RoomEventRepository(database, fixedClock)
        repository.save(event)

        assertEquals(
            RepositoryResult.Applied,
            repository.recordEvidence(event.id, EvidenceReference("ev-device-${event.id.value}")),
        )
        assertEquals(
            RepositoryResult.Applied,
            repository.recordDeliveryAttempt(
                event.id,
                DeliveryInformation(
                    channel = "device-test",
                    status = DeliveryStatus.SUCCESS,
                    occurredAt = fixedClock.instant(),
                ),
            ),
        )
        database.close()

        database = open(name)
        val stored = assertNotNull(RoomEventRepository(database, fixedClock).find(event.id))
        assertEquals(
            "Evidence recorded off the save path must be durable",
            listOf("ev-device-${event.id.value}"),
            stored.evidenceReferences.map { it.id },
        )
        assertEquals(
            listOf(DeliveryStatus.SUCCESS),
            stored.deliveryInformation.map { it.status },
        )
        database.close()
        delete(name)
    }

    @Test
    fun artifacts_cannot_be_recorded_for_an_event_that_does_not_exist() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val missing = EventId.from(UUID.randomUUID())

        assertEquals(RepositoryResult.NotFound, repository.recordEvidence(missing, EvidenceReference("ev-orphan")))
        assertEquals(
            "A delivery attempt needs a parent event, never an orphan row",
            RepositoryResult.NotFound,
            repository.recordDeliveryAttempt(
                missing,
                DeliveryInformation(channel = "device-test", status = DeliveryStatus.SUCCESS),
            ),
        )
        database.close()
        delete(name)
    }

    @Test
    fun metadata_the_database_cannot_persist_is_rejected_rather_than_dropped() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val event = event()
        repository.save(event)

        val rejected = repository.updateState(
            event.id,
            EventState.INITIATED,
            EventState.COLLECTING,
            mapOf("activationCount" to "2"),
        )

        assertTrue(
            "Silently dropping metadata would make a recorded fact disappear: $rejected",
            rejected is RepositoryResult.Rejected,
        )
        assertEquals(
            "A rejected update must not leave a half-applied state",
            EventState.INITIATED,
            repository.find(event.id)?.state,
        )
        database.close()
        delete(name)
    }

    @Test
    fun evidence_and_delivery_rows_require_a_parent_event() {
        val name = databaseName()
        val database = open(name)
        val missingEvent = UUID.randomUUID().toString()

        assertFailsWithForeignKey {
            database.evidenceReferenceDao().insert(
                EvidenceReferenceEntity(
                    evidenceId = UUID.randomUUID().toString(),
                    eventId = missingEvent,
                    kind = "PHOTO",
                    contentReference = "safe-reference",
                    capturedAtEpochMs = null,
                ),
            )
        }
        database.close()
        delete(name)
    }

    @Test
    fun local_storage_factory_uses_no_backup_directory_and_closes_cleanly() {
        val name = databaseName()
        val storage = LocalShieldraStorage.create(
            context = context,
            databaseName = name,
        )

        assertEquals(context.noBackupFilesDir, storage.evidenceFilesRoot().parentFile)
        storage.close()
        delete(name)
    }

    @Test
    fun local_storage_factory_rejects_evidence_directory_outside_no_backup_directory() {
        val name = databaseName()
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                LocalShieldraStorage.create(
                    context = context,
                    databaseName = name,
                    evidenceDirectory = context.filesDir,
                )
            }
        } finally {
            delete(name)
        }
    }

    private fun recordingEvidence(repository: RoomEventRepository): EvidenceStage = EvidenceStage { stored ->
        check(
            repository.recordEvidence(stored.id, EvidenceReference("ev-device-${stored.id.value}")) is RepositoryResult.Applied,
        ) { "Test evidence could not be recorded on the device" }
        StageOutcome.Completed
    }

    private fun open(name: String): ShieldraDatabase = Room.databaseBuilder(
        context,
        ShieldraDatabase::class.java,
        name,
    ).build()

    private fun event(): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = fixedClock.instant(),
    )

    private fun databaseName(): String = "room-integration-${UUID.randomUUID()}.db"

    private fun delete(name: String) {
        context.deleteDatabase(name)
    }

    private fun LocalShieldraStorage.evidenceFilesRoot() =
        java.io.File(context.noBackupFilesDir, "shieldra-evidence")

    private fun assertFailsWithForeignKey(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected a foreign-key constraint failure")
        } catch (error: Exception) {
            val message = error.message.orEmpty() + error.cause?.message.orEmpty()
            check("FOREIGN KEY" in message.uppercase()) {
                "Expected a foreign-key failure, got ${error::class.simpleName}: $message"
            }
        }
    }
}