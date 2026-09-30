package com.shieldra.domain

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Panic is an explicit user activation that must become exactly one persisted
 * confirmed event. These tests pin the approved shape: no attempt counter, no
 * confirmation delay, no cooldown, and no invented time window — the boundary
 * between "the same incident" and "a new incident" is the event lifecycle.
 */
class PanicIncidentCoordinatorTest {
    private val observedAt = Instant.parse("2026-09-29T08:00:00Z")

    @Test
    fun first_panic_creates_exactly_one_real_event_id() {
        val repository = InMemoryEventRepository()
        val identities = CountingIdentities()
        val coordinator = coordinator(repository, identities)

        val activation = coordinator.activate()

        val recorded = assertIs<PanicActivation.Recorded>(activation)
        assertEquals(1, identities.issued.size, "One press must consume exactly one EventId")
        assertEquals(recorded.event.id, identities.issued.single())
        assertEquals(EventState.INITIATED, recorded.event.state)
        assertEquals(GuardSeverity.CRITICAL, recorded.event.severity)
        assertEquals(1, repository.listRecent(50).size)
        assertEquals(recorded.event.id, repository.listRecent(50).single().id)
        assertEquals(1, coordinator.activationCount(recorded.event.id))
    }

    @Test
    fun repeated_panic_while_the_event_is_not_terminal_reuses_the_same_event_id() {
        val repository = InMemoryEventRepository()
        val identities = CountingIdentities()
        val coordinator = coordinator(repository, identities)

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        val second = assertIs<PanicActivation.BoundToActive>(coordinator.activate())
        val third = assertIs<PanicActivation.BoundToActive>(coordinator.activate())

        assertEquals(first.event.id, second.event.id)
        assertEquals(first.event.id, third.event.id)
        assertEquals(1, identities.issued.size, "Binding a repeat must not mint a new EventId")
        assertEquals(1, repository.listRecent(50).size, "Three presses must still be one event")
    }

    @Test
    fun repeated_panic_does_not_start_duplicate_delivery_or_evidence_work() {
        val repository = InMemoryEventRepository()
        val coordinator = coordinator(repository, CountingIdentities())

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        repeat(3) { coordinator.activate() }

        val stored = assertNotNull(repository.find(first.event.id))
        assertTrue(
            stored.evidenceReferences.isEmpty(),
            "Nothing collected evidence, so the event must not claim any",
        )
        assertTrue(
            stored.deliveryInformation.isEmpty(),
            "Nothing was notified, so the event must not claim a delivery attempt",
        )
        assertEquals(EventState.INITIATED, stored.state)
    }

    @Test
    fun activation_count_and_last_activation_stay_consistent_with_the_presses() {
        val clock = FixedClock()
        val repository = InMemoryEventRepository()
        val coordinator = PanicIncidentCoordinator(
            repository = repository,
            identityProvider = CountingIdentities(),
            clock = clock,
        )

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        clock.advanceTo(Instant.parse("2026-09-29T08:01:00Z"))
        val second = assertIs<PanicActivation.BoundToActive>(coordinator.activate())
        clock.advanceTo(Instant.parse("2026-09-29T08:02:00Z"))
        val third = assertIs<PanicActivation.BoundToActive>(coordinator.activate())

        assertEquals(1, first.activationCount)
        assertEquals(2, second.activationCount)
        assertEquals(3, third.activationCount)
        assertEquals(3, coordinator.activationCount(first.event.id))
        assertEquals(Instant.parse("2026-09-29T08:02:00Z"), coordinator.lastActivatedAt(first.event.id))
        assertNull(coordinator.lastActivatedAt(EventId.from(UUID.randomUUID())))
    }

    @Test
    fun a_new_panic_after_the_previous_event_is_terminal_creates_a_new_event_id() {
        val repository = InMemoryEventRepository()
        val identities = CountingIdentities()
        val coordinator = coordinator(repository, identities)

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        assertEquals(
            RepositoryResult.Applied,
            repository.updateState(first.event.id, EventState.INITIATED, EventState.FAILED_FINAL),
        )

        val second = assertIs<PanicActivation.Recorded>(coordinator.activate())

        assertTrue(first.event.id != second.event.id, "A terminal event must not absorb a new incident")
        assertEquals(2, identities.issued.size)
        assertEquals(1, second.activationCount)
        assertEquals(2, repository.listRecent(50).size)
    }

    @Test
    fun a_bound_repeat_is_not_recorded_as_a_separate_incident() {
        val repository = InMemoryEventRepository()
        val coordinator = coordinator(repository, CountingIdentities())

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        val repeat = assertIs<PanicActivation.BoundToActive>(coordinator.activate())
        val expired = assertIs<PanicActivation.Recorded>(
            run {
                repository.updateState(first.event.id, EventState.INITIATED, EventState.EXPIRED)
                coordinator.activate()
            },
        )

        assertEquals(first.event.id, repeat.event.id)
        assertTrue(expired.event.id != first.event.id)
        assertEquals(2, repository.listRecent(50).size)
    }

    @Test
    fun concurrent_panic_presses_cannot_create_two_active_events() {
        val repository = InMemoryEventRepository()
        val identities = CountingIdentities()
        val coordinator = coordinator(repository, identities)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val activations = java.util.concurrent.ConcurrentLinkedQueue<PanicActivation>()

        repeat(threads) {
            pool.execute {
                start.await()
                try {
                    activations += coordinator.activate()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "Concurrent activations must not deadlock")
        pool.shutdownNow()

        val events = repository.listRecent(50)
        assertEquals(1, events.size, "Simultaneous presses are one incident, not one event each")
        assertEquals(1, identities.issued.size)
        assertEquals(1, activations.count { it is PanicActivation.Recorded })
        assertEquals(threads, activations.count { it is PanicActivation.BoundToActive } + activations.count { it is PanicActivation.Recorded })
        assertEquals(threads, coordinator.activationCount(events.single().id))
    }

    @Test
    fun a_duplicate_event_id_is_bound_instead_of_becoming_a_second_confirmed_event() {
        val base = InMemoryEventRepository()
        val repository = object : EventRepository by base {
            override fun listRecent(limit: Int): List<SecurityEvent> = emptyList()
        }
        val coordinator = PanicIncidentCoordinator(
            repository = repository,
            identityProvider = FixedIdentity(EventId.from(UUID.randomUUID())),
        )

        val first = assertIs<PanicActivation.Recorded>(coordinator.activate())
        val second = assertIs<PanicActivation.BoundToActive>(coordinator.activate())

        assertEquals(first.event.id, second.event.id)
        assertEquals(1, base.listRecent(50).size, "Persistence idempotency must not add a second row")
    }

    @Test
    fun a_panic_that_cannot_be_persisted_is_rejected_instead_of_claimed() {
        val base = InMemoryEventRepository()
        val repository = object : EventRepository by base {
            override fun save(event: SecurityEvent): RepositoryResult =
                RepositoryResult.Rejected("injected storage failure")
        }
        val coordinator = coordinator(repository, CountingIdentities())

        val activation = coordinator.activate()

        val rejected = assertIs<PanicActivation.Rejected>(activation)
        assertEquals("injected storage failure", rejected.reason)
        assertTrue(base.listRecent(50).isEmpty(), "An unpersisted panic must not appear in history")
    }

    @Test
    fun the_recorded_event_is_the_persisted_event() {
        val repository = InMemoryEventRepository()
        val coordinator = coordinator(repository, CountingIdentities())

        val activation = assertIs<PanicActivation.Recorded>(coordinator.activate())
        val stored = assertNotNull(repository.find(activation.event.id))

        assertEquals(stored.id, activation.event.id)
        assertEquals(stored.state, activation.event.state)
        assertEquals(stored.createdAt, activation.event.createdAt)
        assertEquals(SecurityEventType.CONFIRMED_SECURITY_EVENT, stored.type)
    }

    private fun coordinator(
        repository: EventRepository,
        identities: IdentityProvider,
    ): PanicIncidentCoordinator = PanicIncidentCoordinator(
        repository = repository,
        identityProvider = identities,
        clock = Clock { observedAt },
    )

    private class CountingIdentities : IdentityProvider {
        val issued = mutableListOf<EventId>()

        override fun nextEventId(): EventId = EventId.from(UUID.randomUUID()).also { issued += it }
    }

    private class FixedIdentity(private val id: EventId) : IdentityProvider {
        override fun nextEventId(): EventId = id
    }

    private class FixedClock : Clock {
        private var instant: Instant = Instant.parse("2026-09-29T08:00:00Z")

        override fun now(): Instant = instant

        fun advanceTo(next: Instant) {
            instant = next
        }
    }
}
