package com.shieldra.storage.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.domain.DeliveryStage
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.InMemoryEventPipeline
import com.shieldra.domain.PipelineResult
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.StageOutcome
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
            delivery = DeliveryStage {
                attempts += 1
                StageOutcome.Deferred
            },
            now = { fixedClock.instant() },
        )

        firstPipeline.process(event.id)
        firstPipeline.process(event.id)
        val deferred = firstPipeline.process(event.id)
        assertTrue(deferred is PipelineResult.Deferred)
        assertEquals(EventState.DEFERRED, repository.find(event.id)?.state)
        database.close()

        database = open(name)
        val reopenedRepository = RoomEventRepository(database, fixedClock)
        val resumed = InMemoryEventPipeline(
            repository = reopenedRepository,
            delivery = DeliveryStage {
                attempts += 1
                StageOutcome.Completed
            },
            now = { fixedClock.instant() },
        ).process(event.id)

        assertTrue(resumed is PipelineResult.Advanced)
        assertEquals(EventState.DELIVERED, reopenedRepository.find(event.id)?.state)
        assertEquals(2, attempts)
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
