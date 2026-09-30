package com.shieldra.domain

import java.time.Clock as JavaClock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The real identity source: every confirmed event gets a fresh UUID. */
object UuidIdentityProvider : IdentityProvider {
    override fun nextEventId(): EventId = EventId.from(UUID.randomUUID())
}

/** What one explicit panic activation produced. */
sealed interface PanicActivation {
    /** A new confirmed event, already persisted before any response work. */
    data class Recorded(val event: SecurityEvent, val activationCount: Int) : PanicActivation

    /** The press joined the incident that is still running. */
    data class BoundToActive(val event: SecurityEvent, val activationCount: Int) : PanicActivation

    /** Nothing was recorded; the reason is the persistence or confirmation failure. */
    data class Rejected(val reason: String) : PanicActivation
}

/**
 * Turns an explicit user activation into exactly one confirmed event.
 *
 * Panic has no attempt counter, no confirmation delay, no cooldown, and no
 * time window: while an event is non-terminal every further press binds to
 * that same event, and only a terminal event starts a new one. The event is
 * persisted before anything else happens, so a press that could not be stored
 * is reported as a rejection rather than as a recorded incident.
 *
 * The activation count is this session's tally. It is deliberately not written
 * into [SecurityEvent.metadata]: schema v2 storage persists no metadata keys
 * besides the deferred stage, and silently dropping a recorded fact would be
 * exactly the kind of claim this batch exists to remove.
 */
class PanicIncidentCoordinator(
    private val repository: EventRepository,
    private val identityProvider: IdentityProvider = UuidIdentityProvider,
    private val engine: ProtectionStateEngine = DefaultProtectionStateEngine(),
    private val clock: Clock = Clock { Instant.now(JavaClock.systemUTC()) },
    private val activeEventScanLimit: Int = ACTIVE_EVENT_SCAN_LIMIT,
) {
    private val activations = ConcurrentHashMap<EventId, ActivationRecord>()

    fun activationCount(eventId: EventId): Int = activations[eventId]?.count ?: 0

    fun lastActivatedAt(eventId: EventId): Instant? = activations[eventId]?.lastActivatedAt

    @Synchronized
    fun activate(): PanicActivation {
        val active = repository.listRecent(activeEventScanLimit).firstOrNull { !it.state.isTerminal() }
        if (active != null) {
            return PanicActivation.BoundToActive(active, record(active.id, clock.now()))
        }

        val evaluation = engine.evaluate(
            GuardSignal(
                kind = GuardKind.PANIC,
                observedAt = clock.now(),
                observation = GuardObservation.PanicActivated,
            ),
            clock,
            identityProvider,
        )
        val event = evaluation.event
        if (evaluation.confirmation.status != GuardConfirmationStatus.CONFIRMED || event == null) {
            return PanicActivation.Rejected(
                evaluation.confirmation.reason ?: "Panic activation did not confirm a security event",
            )
        }

        return when (val saved = repository.save(event)) {
            RepositoryResult.Applied -> PanicActivation.Recorded(event, record(event.id, clock.now()))
            RepositoryResult.AlreadyExists -> {
                val stored = repository.find(event.id) ?: event
                PanicActivation.BoundToActive(stored, record(stored.id, clock.now()))
            }
            is RepositoryResult.Conflict -> PanicActivation.Rejected(
                "Event persistence conflicted with state ${saved.current}",
            )
            RepositoryResult.NotFound -> PanicActivation.Rejected("Event persistence found no target row")
            is RepositoryResult.Rejected -> PanicActivation.Rejected(saved.reason)
        }
    }

    private fun record(eventId: EventId, at: Instant): Int {
        val next = (activations[eventId]?.count ?: 0) + 1
        activations[eventId] = ActivationRecord(next, at)
        return next
    }

    private data class ActivationRecord(
        val count: Int,
        val lastActivatedAt: Instant,
    }

    private companion object {
        /** A panic incident is the newest active event; the list is bounded well below this. */
        const val ACTIVE_EVENT_SCAN_LIMIT = 100
    }
}
