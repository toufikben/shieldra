package com.shieldra.domain

import java.time.Clock as JavaClock
import java.time.Duration
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
 * Panic has no attempt counter, no confirmation delay, and no cooldown. What it
 * does have is a reuse window (amendment A-28): a press joins the open Panic
 * incident only while that incident is at most [PANIC_REUSE_WINDOW_MINUTES] old
 * measured from its own start, and past the window a press becomes a new incident
 * even when the old one never reached a terminal state. Without the window an
 * event that never terminated would absorb every later press forever, and the
 * bounded scan would only hide the newest ones rather than bound the incident.
 *
 * The event is persisted before anything else happens, so a press that could not
 * be stored is reported as a rejection rather than as a recorded incident.
 *
 * Amendment A-7: the activation count is persisted beside the event through
 * [PanicActivationStore] when the repository supports it, so a restart recovers
 * the real count instead of starting over. The in-process [activations] map is a
 * read-back cache for the values this class has to report — never the source of
 * truth. A repository without a durable store still works, but its count resets
 * on restart, and nothing in the UI presents that count as durable.
 */
class PanicIncidentCoordinator(
    private val repository: EventRepository,
    private val identityProvider: IdentityProvider = UuidIdentityProvider,
    private val engine: ProtectionStateEngine = DefaultProtectionStateEngine(),
    private val clock: Clock = Clock { Instant.now(JavaClock.systemUTC()) },
    private val activeEventScanLimit: Int = ACTIVE_EVENT_SCAN_LIMIT,
) {
    private val activations = ConcurrentHashMap<EventId, ActivationRecord>()

    /** Null when storage cannot hold the counter; the session tally then applies. */
    private val store: PanicActivationStore? = repository as? PanicActivationStore

    fun activationCount(eventId: EventId): Int = activations[eventId]?.count ?: 0

    fun lastActivatedAt(eventId: EventId): Instant? = activations[eventId]?.lastActivatedAt

    @Synchronized
    fun activate(): PanicActivation {
        val at = clock.now()
        // Only a Panic incident may absorb a Panic press. Without the type
        // filter, the first non-terminal event of any future guard would be
        // adopted here and its counters corrupted (amendment A-7). The age test
        // is what stops an incident that never terminated from staying open
        // forever (amendment A-28), and it reads the incident's own persisted
        // start, so a process restart cannot extend it.
        val active = repository.listRecent(activeEventScanLimit)
            .firstOrNull {
                it.type == SecurityEventType.PANIC_ACTIVATION &&
                    !it.state.isTerminal() &&
                    withinReuseWindow(it, at)
            }
        if (active != null) {
            val durable = store
            if (durable == null) {
                val next = (activations[active.id]?.count ?: 0) + 1
                return PanicActivation.BoundToActive(active, remember(active.id, next, at))
            }
            when (durable.recordPanicActivation(active.id, at)) {
                is RepositoryResult.Applied -> {
                    val count = durable.panicActivationCount(active.id)
                        ?: return activateNewIncident(at)
                    return PanicActivation.BoundToActive(active, remember(active.id, count, at))
                }
                // Storage refused: the event stopped being active between the
                // scan and the write. Starting a fresh incident is the only
                // honest reading — reusing a count would claim a press landed on
                // an event that cannot take one.
                else -> return activateNewIncident(at)
            }
        }

        return activateNewIncident(at)
    }

    /**
     * True while [SecurityEvent.createdAt] is at most [PANIC_REUSE_WINDOW_MINUTES]
     * behind [at]. The boundary is inclusive, and an event stamped in the future
     * (a clock that moved backwards) is inside the window: it is reusable, never
     * silently replaced by a second open incident.
     */
    private fun withinReuseWindow(event: SecurityEvent, at: Instant): Boolean =
        Duration.between(event.createdAt, at) <= PANIC_REUSE_WINDOW

    private fun activateNewIncident(at: Instant): PanicActivation {
        val evaluation = engine.evaluate(
            GuardSignal(
                kind = GuardKind.PANIC,
                observedAt = at,
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

        val panicked = event.asPanicActivation(count = 1, at = at)
        return when (val saved = repository.save(panicked)) {
            is RepositoryResult.Applied -> PanicActivation.Recorded(panicked, remember(panicked.id, 1, at))
            is RepositoryResult.AlreadyExists -> {
                val stored = repository.find(panicked.id) ?: panicked
                PanicActivation.BoundToActive(stored, remember(stored.id, 1, at))
            }
            is RepositoryResult.Conflict -> PanicActivation.Rejected(
                "Event persistence conflicted with state ${saved.current}",
            )
            RepositoryResult.NotFound -> PanicActivation.Rejected("Event persistence found no target row")
            is RepositoryResult.Rejected -> PanicActivation.Rejected(saved.reason)
        }
    }

    /** Counts live in storage; this only caches what [PanicActivation] must report. */
    private fun remember(eventId: EventId, count: Int, at: Instant): Int {
        activations[eventId] = ActivationRecord(count, at)
        return count
    }

    /**
     * Tags the engine's event as a Panic activation and stamps the first count
     * onto the very same object, so the initial write already carries the
     * counter and a crash cannot leave a saved Panic event without it.
     */
    private fun SecurityEvent.asPanicActivation(count: Int, at: Instant): SecurityEvent =
        SecurityEvent.create(
            eventId = id,
            createdAt = createdAt,
            expiresAt = expiresAt,
            state = state,
            severity = severity,
            evidenceReferences = evidenceReferences,
            deliveryInformation = deliveryInformation,
            metadata = metadata,
            type = SecurityEventType.PANIC_ACTIVATION,
            panicActivationCount = count,
            panicLastActivatedAt = at,
        )

    private data class ActivationRecord(
        val count: Int,
        val lastActivatedAt: Instant,
    )

    private companion object {
        /** A panic incident is the newest active event; the list is bounded well below this. */
        const val ACTIVE_EVENT_SCAN_LIMIT = 100

        /**
         * The owner-approved reuse window for Panic (Q-K-8, amendment A-28). Kept as a named
         * constant so no call site can restate a different deadline.
         */
        const val PANIC_REUSE_WINDOW_MINUTES: Long = 10L

        val PANIC_REUSE_WINDOW: Duration = Duration.ofMinutes(PANIC_REUSE_WINDOW_MINUTES)
    }
}
