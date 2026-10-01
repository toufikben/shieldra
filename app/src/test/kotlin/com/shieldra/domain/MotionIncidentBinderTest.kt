package com.shieldra.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Batch G / slice G5, Q-G5-c (amendment A-18): same-event deduplication for Motion, scoped to Motion.
 *
 * The owner approved reusing the active non-terminal event's `EventId` and rejected the general
 * "first non-terminal event" rule: a confirmed Movement must never be attached to somebody else's
 * open incident, which is exactly the corruption A-7 named for Panic. The boundary between "the same
 * incident" and "a new one" is the event lifecycle — there is no cooldown, and no new number.
 */
class MotionIncidentBinderTest {
    private val observedAt = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = Clock { observedAt }

    @Test
    fun a_confirmed_movement_becomes_one_persisted_event_tagged_as_motion() {
        val repository = InMemoryEventRepository()
        val binder = binder(repository)

        val confirmation = assertIs<MotionConfirmation.Recorded>(binder.confirm(strongMovement()))

        assertEquals(GuardSeverity.HIGH, confirmation.event.severity)
        assertEquals(
            SecurityEventType.MOTION_CONFIRMED_EVENT,
            confirmation.event.type,
            "A-18: the tag is what makes the next confirmation bind to Motion and only to Motion",
        )
        assertEquals(confirmation.event.id, repository.find(confirmation.event.id)?.id)
    }

    @Test
    fun a_second_confirmed_movement_binds_to_the_active_motion_event_and_persists_no_second_incident() {
        val repository = InMemoryEventRepository()
        val binder = binder(repository, CountingIdentities())

        val first = assertIs<MotionConfirmation.Recorded>(binder.confirm(strongMovement()))
        val second = assertIs<MotionConfirmation.BoundToActive>(binder.confirm(strongMovement()))

        assertEquals(first.event.id, second.event.id, "same-event deduplication, not a second incident")
        assertEquals(1, repository.listRecent(50).size)
    }

    @Test
    fun two_confirmations_in_the_same_instant_are_deduplicated_without_any_cooldown() {
        val repository = InMemoryEventRepository()
        val binder = binder(repository, CountingIdentities())

        val first = assertIs<MotionConfirmation.Recorded>(binder.confirm(strongMovement()))
        // One clock, one instant: the only thing separating this from a cooldown is that the rule is
        // the event's lifecycle, so a repeat at any time while it is non-terminal binds the same way.
        val second = assertIs<MotionConfirmation.BoundToActive>(binder.confirm(strongMovement()))

        assertEquals(first.event.id, second.event.id)
        assertEquals(1, repository.listRecent(50).size)
    }

    @Test
    fun an_active_motion_confirmation_never_binds_to_a_lock_sim_battery_or_panic_event() {
        val repository = InMemoryEventRepository()
        // Newest-first: a plain "first non-terminal event" rule would adopt the Panic and the Lock
        // sequence below instead of starting a Motion incident, which is the hazard A-18 names.
        val openPanic = seed(repository, SecurityEventType.PANIC_ACTIVATION, observedAt.plusSeconds(60))
        val openOtherGuard = seed(repository, SecurityEventType.CONFIRMED_SECURITY_EVENT, observedAt.plusSeconds(120))
        val identities = CountingIdentities()

        val confirmation = assertIs<MotionConfirmation.Recorded>(binder(repository, identities).confirm(strongMovement()))

        assertTrue(confirmation.event.id != openPanic.id, "a Movement must not attach to a Panic incident")
        assertTrue(
            confirmation.event.id != openOtherGuard.id,
            "a Movement must not attach to another guard's open event",
        )
        assertEquals(1, identities.issued.size)
        assertEquals(SecurityEventType.MOTION_CONFIRMED_EVENT, confirmation.event.type)
    }

    @Test
    fun a_terminal_motion_event_starts_a_new_incident() {
        val repository = InMemoryEventRepository()
        val delivered = seed(repository, SecurityEventType.MOTION_CONFIRMED_EVENT, observedAt, EventState.DELIVERED)
        assertTrue(delivered.state.isTerminal(), "the fixture must really be terminal")
        val identities = CountingIdentities()

        val confirmation = assertIs<MotionConfirmation.Recorded>(binder(repository, identities).confirm(strongMovement()))

        assertTrue(confirmation.event.id != delivered.id, "a closed incident cannot absorb a new movement")
    }

    @Test
    fun binding_a_repeat_claims_no_evidence_and_no_delivery() {
        val repository = InMemoryEventRepository()
        val binder = binder(repository, CountingIdentities())

        val first = assertIs<MotionConfirmation.Recorded>(binder.confirm(strongMovement()))
        repeat(3) { binder.confirm(strongMovement()) }

        val stored = assertNotNull(repository.find(first.event.id))
        assertTrue(stored.evidenceReferences.isEmpty(), "Motion monitoring collects no evidence (§21-§23)")
        assertTrue(stored.deliveryInformation.isEmpty(), "nothing was delivered, so the event may not claim it")
    }

    @Test
    fun an_unconfirmed_movement_is_not_persisted_as_a_security_event() {
        val repository = InMemoryEventRepository()
        val binder = binder(repository, CountingIdentities())

        val confirmation = binder.confirm(shortEpisode())

        assertIs<MotionConfirmation.NotConfirmed>(confirmation)
        assertTrue(repository.listRecent(50).isEmpty(), "a signal is not an event")
    }

    @Test
    fun a_movement_that_cannot_be_persisted_is_rejected_rather_than_claimed() {
        val base = InMemoryEventRepository()
        val repository = object : EventRepository by base {
            override fun save(event: SecurityEvent): RepositoryResult =
                RepositoryResult.Rejected("injected storage failure")
        }

        val confirmation = assertIs<MotionConfirmation.Rejected>(binder(repository, CountingIdentities()).confirm(strongMovement()))

        assertEquals("injected storage failure", confirmation.reason)
        assertTrue(base.listRecent(50).isEmpty(), "an unpersisted event must not appear in history")
    }

    private fun binder(repository: EventRepository, identities: IdentityProvider = CountingIdentities()) =
        MotionIncidentBinder(
            repository = repository,
            identityProvider = identities,
            clock = clock,
        )

    /** Three seconds of valid strong movement: the owner's confirmation rule, nothing invented. */
    private fun strongMovement(): GuardSignal = GuardSignal(
        kind = GuardKind.MOTION,
        observedAt = observedAt,
        observation = GuardObservation.Motion(
            duration = Duration.ofSeconds(4),
            suspiciousOccurrences = 1,
            sensorQuality = SensorQuality.VALID,
        ),
    )

    /** Half a second: an episode by A-17's definition, and nothing more than that. */
    private fun shortEpisode(): GuardSignal = GuardSignal(
        kind = GuardKind.MOTION,
        observedAt = observedAt,
        observation = GuardObservation.Motion(
            duration = Duration.ofMillis(500L),
            suspiciousOccurrences = 1,
            sensorQuality = SensorQuality.VALID,
        ),
    )

    private fun seed(
        repository: EventRepository,
        type: SecurityEventType,
        createdAt: Instant,
        state: EventState = EventState.INITIATED,
    ): SecurityEvent {
        val event = SecurityEvent.create(
            eventId = EventId.from(UUID.randomUUID()),
            createdAt = createdAt,
            state = state,
            severity = GuardSeverity.HIGH,
            type = type,
        )
        assertEquals(RepositoryResult.Applied, repository.save(event))
        return event
    }

    private class CountingIdentities : IdentityProvider {
        val issued = mutableListOf<EventId>()

        override fun nextEventId(): EventId = EventId.from(UUID.randomUUID()).also { issued += it }
    }
}
