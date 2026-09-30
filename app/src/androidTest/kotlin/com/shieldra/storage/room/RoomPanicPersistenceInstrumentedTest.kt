package com.shieldra.storage.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shieldra.app.presentation.guard.PersistedEventLabels
import com.shieldra.app.presentation.guard.PersistedEventReader
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.domain.Clock
import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.IdentityProvider
import com.shieldra.domain.PanicActivation
import com.shieldra.domain.PanicIncidentCoordinator
import com.shieldra.domain.RepositoryResult
import java.time.Clock as JavaClock
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Panic persistence on the device's real database file: one explicit activation
 * is one durable event, repeats bind to it, and nothing on disk claims evidence
 * or a delivery that never happened.
 */
@RunWith(AndroidJUnit4::class)
class RoomPanicPersistenceInstrumentedTest {
    private val fixedClock = JavaClock.fixed(
        Instant.parse("2026-09-29T08:00:00Z"),
        java.time.ZoneOffset.UTC,
    )
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun a_panic_sequence_persists_one_event_on_the_device_filesystem() {
        val name = databaseName()
        var database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val coordinator = coordinator(repository)

        val first = coordinator.activate()
        val repeat = coordinator.activate()
        database.close()

        assertTrue(
            "The first press must be persisted before any response work: $first",
            first is PanicActivation.Recorded,
        )
        assertTrue(
            "A second press binds to the same persisted event: $repeat",
            repeat is PanicActivation.BoundToActive,
        )
        assertEquals(
            "Two presses are one incident",
            (first as PanicActivation.Recorded).event.id,
            (repeat as PanicActivation.BoundToActive).event.id,
        )

        database = open(name)
        val stored = RoomEventRepository(database, fixedClock).listRecent(50)
        assertEquals("Two presses are one event on the device", 1, stored.size)
        assertEquals(EventState.INITIATED, stored.single().state)
        assertTrue(
            "No evidence was collected on the device",
            stored.single().evidenceReferences.isEmpty(),
        )
        assertTrue(
            "Nothing was notified on the device",
            stored.single().deliveryInformation.isEmpty(),
        )
        database.close()
        delete(name)
    }

    @Test
    fun a_panic_after_a_terminal_event_persists_a_second_event() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val coordinator = coordinator(repository)

        val first = coordinator.activate() as PanicActivation.Recorded
        coordinator.activate()
        assertEquals(
            RepositoryResult.Applied,
            repository.updateState(first.event.id, EventState.INITIATED, EventState.FAILED_FINAL),
        )

        val second = coordinator.activate()

        assertTrue(
            "A terminal event must not absorb a new incident: $second",
            second is PanicActivation.Recorded,
        )
        val stored = repository.listRecent(50)
        assertEquals(2, stored.size)
        assertNotNull(stored.firstOrNull { it.id == (second as PanicActivation.Recorded).event.id })
        assertTrue(
            "The new incident must not inherit the old one's id",
            stored.map { it.id }.toSet().size == 2,
        )
        database.close()
        delete(name)
    }

    @Test
    fun device_history_claims_no_evidence_and_no_receipt_for_a_recorded_panic() {
        val name = databaseName()
        val database = open(name)
        val repository = RoomEventRepository(database, fixedClock)
        val activation = coordinator(repository).activate() as PanicActivation.Recorded

        val reader = PersistedEventReader(
            storageInstalled = true,
            loadRecent = { limit -> repository.listRecent(limit) },
            labels = PersistedEventLabels(title = "Security event"),
        )
        val projected = reader.readRecent(limit = 20, now = fixedClock.instant()).single()
        val detail = assertNotNull(
            "A persisted panic must be reachable from the device history",
            reader.readOne(activation.event.id.value.toString(), now = fixedClock.instant()),
        )

        assertTrue("History must not claim a photo that was never taken", !projected.hasPhoto)
        assertTrue("History must not claim a location that was never taken", !projected.hasLocation)
        assertEquals(EventState.INITIATED.name, projected.subtitle)
        assertEquals(
            "A recorded event that never entered delivery must not read as delivered",
            com.shieldra.app.presentation.model.DeliveryStatus.Skipped,
            projected.deliveryStatus,
        )
        assertTrue("The detail must show no delivery receipt", detail.delivery.isEmpty())
        assertTrue("The detail must show no photo", !detail.hasPhoto)
        assertEquals(LocationKind.Unavailable, detail.location.kind)
        assertTrue(
            "No successful delivery attempt row may exist for a panic that never sent",
            repository.find(activation.event.id)!!.deliveryInformation
                .none { it.status == DeliveryStatus.SUCCESS },
        )
        database.close()
        delete(name)
    }

    private fun coordinator(repository: RoomEventRepository) = PanicIncidentCoordinator(
        repository = repository,
        identityProvider = IdentityProvider { EventId.from(UUID.randomUUID()) },
        clock = Clock { fixedClock.instant() },
    )

    private fun open(name: String): ShieldraDatabase = Room.databaseBuilder(
        context,
        ShieldraDatabase::class.java,
        name,
    ).build()

    private fun databaseName(): String = "room-panic-${UUID.randomUUID()}.db"

    private fun delete(name: String) {
        context.deleteDatabase(name)
    }
}
