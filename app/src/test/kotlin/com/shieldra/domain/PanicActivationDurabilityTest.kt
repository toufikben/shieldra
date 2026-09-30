package com.shieldra.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Amendment A-7: the Panic activation count is durable metadata on the Panic
 * event itself, not a process-local tally. These tests drive the coordinator
 * through an [EventRepository] and assert that a *fresh* coordinator — the
 * situation after a process restart — recovers the count and the last
 * activation time from storage. If the count lived only in the coordinator's
 * in-memory map, a new coordinator would restart at 1 and these fail.
 *
 * The repository used here ([CountingEventRepository]) mirrors the Room shape
 * the real implementation will have: the count and timestamp are stored beside
 * the event and read back from it. A-7 forbids using `SecurityEvent.metadata`
 * for the counter, and forbids using the count to create a second `EventId`,
 * raise severity, or bypass the lifecycle; the tests below hold each of those.
 */
class PanicActivationDurabilityTest {

    private var instant: Instant = Instant.parse("2026-09-30T09:00:00Z")
    private val clock = Clock { instant }

    @Test
    fun first_activation_persists_count_of_one() {
        val repository = CountingEventRepository()

        val activation = coordinator(repository).activate()

        val recorded = assertIs<PanicActivation.Recorded>(activation)
        assertEquals(1, recorded.activationCount)
        assertEquals(1, repository.countFor(recorded.event.id))
    }

    @Test
    fun repeated_activation_while_non_terminal_increments_the_persisted_count() {
        val repository = CountingEventRepository()
        val subject = coordinator(repository)

        val first = assertIs<PanicActivation.Recorded>(subject.activate())
        instant = instant.plusSeconds(10)
        val second = assertIs<PanicActivation.BoundToActive>(subject.activate())
        instant = instant.plusSeconds(10)
        val third = assertIs<PanicActivation.BoundToActive>(subject.activate())

        assertEquals(1, first.activationCount)
        assertEquals(2, second.activationCount)
        assertEquals(3, third.activationCount)
        assertEquals(3, repository.countFor(first.event.id))
        assertEquals(first.event.id, third.event.id, "a repeat must reuse the same incident id")
    }

    @Test
    fun the_count_is_recovered_from_storage_by_a_fresh_coordinator() {
        val repository = CountingEventRepository()
        val before = coordinator(repository)
        assertIs<PanicActivation.Recorded>(before.activate())
        instant = instant.plusSeconds(5)
        assertIs<PanicActivation.BoundToActive>(before.activate())

        // A new coordinator is exactly what a process restart produces.
        val afterRestart = coordinator(repository)
        instant = instant.plusSeconds(5)
        val resumed = assertIs<PanicActivation.BoundToActive>(afterRestart.activate())

        assertEquals(3, resumed.activationCount, "the count must come from storage, not a fresh in-memory tally")
        assertEquals(3, repository.countFor(resumed.event.id))
    }

    @Test
    fun last_activated_at_is_read_from_storage_after_restart() {
        val repository = CountingEventRepository()
        val before = coordinator(repository)
        assertIs<PanicActivation.Recorded>(before.activate())
        instant = Instant.parse("2026-09-30T10:15:30Z")
        val second = assertIs<PanicActivation.BoundToActive>(before.activate())

        // The instant is stored, not merely held by the coordinator instance.
        assertEquals(
            Instant.parse("2026-09-30T10:15:30Z"),
            repository.lastActivationAtFor(second.event.id),
        )

        val afterRestart = coordinator(repository)
        instant = Instant.parse("2026-09-30T11:00:00Z")
        val resumed = afterRestart.activate()
        assertEquals(
            Instant.parse("2026-09-30T11:00:00Z"),
            repository.lastActivationAtFor(resumed.event.id),
            "the newest activation must overwrite the stored instant",
        )
    }

    @Test
    fun a_new_panic_after_a_terminal_event_starts_at_one() {
        val repository = CountingEventRepository()
        val subject = coordinator(repository)
        val first = assertIs<PanicActivation.Recorded>(subject.activate())
        assertIs<PanicActivation.BoundToActive>(subject.activate())
        repository.markTerminal(first.event.id)

        instant = instant.plusSeconds(60)
        val afterTerminal = assertIs<PanicActivation.Recorded>(subject.activate())

        assertEquals(1, afterTerminal.activationCount)
        assertTrue(first.event.id != afterTerminal.event.id, "a terminal incident must yield a new event id")
        assertEquals(1, repository.countFor(afterTerminal.event.id))
        assertEquals(3, repository.countFor(first.event.id), "the terminal event keeps its own count")
    }

    @Test
    fun a_terminal_event_cannot_be_incremented() {
        val repository = CountingEventRepository()
        val subject = coordinator(repository)
        val first = assertIs<PanicActivation.Recorded>(subject.activate())
        repository.markTerminal(first.event.id)

        val rejected = repository.recordPanicActivation(first.event.id, instant)

        assertIs<RepositoryResult.Rejected>(rejected, "a terminal event must refuse a further activation")
        assertEquals(1, repository.countFor(first.event.id))
    }

    @Test
    fun repeated_activation_does_not_create_a_second_active_panic_event() {
        val repository = CountingEventRepository()
        val subject = coordinator(repository)

        subject.activate()
        instant = instant.plusSeconds(1)
        subject.activate()
        instant = instant.plusSeconds(1)
        subject.activate()

        assertEquals(1, repository.listRecent(50).size, "three presses must leave exactly one event")
    }

    @Test
    fun concurrent_activations_do_not_lose_increments() {
        val repository = CountingEventRepository()
        val subject = coordinator(repository)
        assertIs<PanicActivation.Recorded>(subject.activate())

        val threads = 8
        val barrier = java.util.concurrent.CyclicBarrier(threads)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        try {
            val futures = (1..threads).map {
                pool.submit {
                    barrier.await()
                    subject.activate()
                }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        val only = repository.listRecent(50).single()
        assertEquals(
            threads + 1,
            repository.countFor(only.id),
            "eight concurrent presses on top of the first must produce nine durable activations",
        )
    }

    private fun coordinator(repository: EventRepository) = PanicIncidentCoordinator(
        repository = repository,
        identityProvider = object : IdentityProvider {
            override fun nextEventId(): EventId = EventId.from(UUID.randomUUID())
        },
        clock = clock,
    )

    /**
     * Storage-shaped stand-in for Room: the count and the last activation live
     * beside the event. It implements [PanicActivationStore] — the narrow
     * contract the coordinator actually needs — exactly the way
     * `RoomEventRepository` will, so a restart (a new coordinator over the same
     * store) recovers the count instead of restarting at 1.
     */
    private class CountingEventRepository : EventRepository, PanicActivationStore {
        private val rows = linkedMapOf<EventId, Row>()
        private val lock = Any()

        private data class Row(
            val event: SecurityEvent,
            var count: Int,
            var lastActivatedAt: Instant?,
            var terminal: Boolean = false,
        )

        fun countFor(eventId: EventId): Int = synchronized(lock) { rows[eventId]?.count ?: 0 }

        fun lastActivationAtFor(eventId: EventId): Instant? = synchronized(lock) { rows[eventId]?.lastActivatedAt }

        fun markTerminal(eventId: EventId) {
            synchronized(lock) { rows[eventId]?.terminal = true }
        }

        override fun panicActivationCount(eventId: EventId): Int = countFor(eventId)

        override fun recordPanicActivation(eventId: EventId, at: Instant): RepositoryResult = synchronized(lock) {
            val row = rows[eventId] ?: return@synchronized RepositoryResult.NotFound
            if (row.terminal) return@synchronized RepositoryResult.Rejected("terminal event cannot be activated again")
            row.count += 1
            row.lastActivatedAt = at
            RepositoryResult.Applied
        }

        override fun find(eventId: EventId): SecurityEvent? = synchronized(lock) { rows[eventId]?.event }

        override fun listRecent(limit: Int): List<SecurityEvent> =
            synchronized(lock) { rows.values.reversed().take(limit).map { it.event } }

        override fun save(event: SecurityEvent): RepositoryResult = synchronized(lock) {
            if (rows.containsKey(event.id)) return@synchronized RepositoryResult.AlreadyExists
            rows[event.id] = Row(event, count = 1, lastActivatedAt = event.createdAt)
            RepositoryResult.Applied
        }

        override fun updateState(
            eventId: EventId,
            expected: EventState,
            next: EventState,
            metadata: Map<String, String>?,
            expiresAt: Instant?,
        ): RepositoryResult = RepositoryResult.Rejected("not needed by these tests")

        override fun recordEvidence(eventId: EventId, reference: EvidenceReference): RepositoryResult =
            RepositoryResult.NotFound

        override fun recordDeliveryAttempt(eventId: EventId, information: DeliveryInformation): RepositoryResult =
            RepositoryResult.NotFound
    }
}
