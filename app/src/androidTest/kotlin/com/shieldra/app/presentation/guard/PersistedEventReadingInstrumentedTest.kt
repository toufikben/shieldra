package com.shieldra.app.presentation.guard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.SecurityEvent
import com.shieldra.storage.room.RoomEventRepository
import com.shieldra.storage.room.ShieldraDatabase
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
 * Proves on a real device that a persisted row survives the database and comes
 * back through the reader the UI actually uses, and that the reader refuses to
 * invent anything the row does not contain.
 */
@RunWith(AndroidJUnit4::class)
class PersistedEventReadingInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-09-29T10:00:00Z")

    @Test
    fun a_persisted_event_is_readable_through_the_projection_reader() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database)
        val created = Instant.parse("2026-09-29T09:30:00Z")
        val persisted = SecurityEvent.create(
            eventId = EventId.from(UUID.randomUUID()),
            createdAt = created,
        )
        assertEquals(com.shieldra.domain.RepositoryResult.Applied, repository.save(persisted))
        val reader = readerFor(repository)

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(persisted.id.value.toString(), projected.id)
        assertEquals("30m ago", projected.timestampLabel)
        assertFalse("No photo was stored, so none may be claimed", projected.hasPhoto)
        assertFalse("No location was stored, so none may be claimed", projected.hasLocation)
        assertEquals(DeliveryStatus.NotAttempted, projected.deliveryStatus)
        database.close()
        delete(name)
    }

    @Test
    fun listRecent_returns_newest_first_and_honours_the_limit() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database)
        val base = Instant.parse("2026-09-29T08:00:00Z")
        val older = SecurityEvent.create(EventId.from(UUID.randomUUID()), base)
        val newer = SecurityEvent.create(EventId.from(UUID.randomUUID()), base.plusSeconds(3600))
        val newest = SecurityEvent.create(EventId.from(UUID.randomUUID()), base.plusSeconds(7200))
        repository.save(older)
        repository.save(newer)
        repository.save(newest)

        val listed = repository.listRecent(2)

        assertEquals(2, listed.size)
        assertEquals(newest.id, listed[0].id)
        assertEquals(newer.id, listed[1].id)
        database.close()
        delete(name)
    }

    @Test
    fun a_persisted_event_is_resolvable_by_its_own_id_and_an_unknown_id_is_not() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database)
        val persisted = SecurityEvent.create(
            eventId = EventId.from(UUID.randomUUID()),
            createdAt = Instant.parse("2026-09-29T09:45:00Z"),
        )
        repository.save(persisted)
        val reader = readerFor(repository)

        val found = reader.readOne(persisted.id.value.toString(), now = now)
        assertNotNull("A stored event must resolve by id", found)
        assertEquals(persisted.id.value.toString(), found!!.id)
        assertNull("An unknown id must not produce a fabricated detail", reader.readOne("evt_missing", now = now))
        database.close()
        delete(name)
    }

    @Test
    fun the_delivery_state_survives_the_round_trip_into_the_projection() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database)
        val persisted = SecurityEvent.create(
            eventId = EventId.from(UUID.randomUUID()),
            createdAt = Instant.parse("2026-09-29T09:50:00Z"),
        )
        repository.save(persisted)
        repository.updateState(persisted.id, EventState.INITIATED, EventState.COLLECTING)
        repository.updateState(persisted.id, EventState.COLLECTING, EventState.READY)
        repository.updateState(persisted.id, EventState.READY, EventState.DELIVERING)
        repository.updateState(persisted.id, EventState.DELIVERING, EventState.DELIVERED)
        val reader = readerFor(repository)

        val projected = reader.readRecent(limit = 20, now = now).single()

        assertEquals(DeliveryStatus.Delivered, projected.deliveryStatus)
        database.close()
        delete(name)
    }

    @Test
    fun a_reader_without_installed_storage_reads_nothing() {
        val reader = PersistedEventReader(
            storageInstalled = false,
            loadRecent = { error("must not touch storage") },
        )

        assertTrue(reader.readRecent(limit = 20, now = now).isEmpty())
        assertNull(reader.readOne("evt_any", now = now))
    }

    private fun readerFor(repository: RoomEventRepository) = PersistedEventReader(
        storageInstalled = true,
        loadRecent = { limit -> repository.listRecent(limit) },
    )

    private fun open(name: String): ShieldraDatabase = Room.databaseBuilder(
        context,
        ShieldraDatabase::class.java,
        name,
    ).build()

    private fun databaseName(): String = "persisted-reading-${UUID.randomUUID()}.db"

    private fun delete(name: String) {
        context.deleteDatabase(name)
    }
}
