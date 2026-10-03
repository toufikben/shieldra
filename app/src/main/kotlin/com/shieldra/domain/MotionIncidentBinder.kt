package com.shieldra.domain

import java.time.Clock as JavaClock
import java.time.Duration
import java.time.Instant

/** What one Movement report produced (amendment A-18). */
sealed interface MotionConfirmation {
    /** A new confirmed Motion incident, persisted before anything claims it. */
    data class Recorded(val event: SecurityEvent) : MotionConfirmation

    /** The Movement joined the Motion incident that is still running. */
    data class BoundToActive(val event: SecurityEvent) : MotionConfirmation

    /** A signal, not an event: nothing was persisted and nothing may be shown as confirmed. */
    data class NotConfirmed(val reason: String? = null) : MotionConfirmation

    /** The event could not be stored, so the confirmation is reported as a failure. */
    data class Rejected(val reason: String) : MotionConfirmation
}

/**
 * Turns a confirmed Movement into exactly one Motion incident.
 *
 * The judgement stays with [ProtectionStateEngine] — this class re-implements no
 * threshold and owns no confirmation rule, so G1-02's three-second/ten-second
 * policy has one home. What is added here is lifetime: while a non-terminal
 * `MOTION_CONFIRMED_EVENT` exists, a further confirmation binds to that same
 * event, and only a terminal one starts a new incident. There is no cooldown.
 *
 * The lifecycle alone was not enough: nothing in the shipping app moves a Motion
 * event to a terminal state, so the first incident absorbed every later Movement
 * for as long as it existed and the guard recorded exactly one event. The same
 * defect was closed for Panic by A-28's reuse window, and the same window bounds
 * Motion here: a Movement joins the open incident only while that incident is at
 * most [MOTION_REUSE_WINDOW_MINUTES] old, measured from its own persisted start.
 *
 * The open incident is remembered between calls, so a repeat costs one primary-key
 * read instead of a scan of the newest rows on the sensor thread.
 *
 * The type filter is the point of the whole design (the hazard A-7 named, seen
 * from the other side): a plain "newest non-terminal event" rule would attach a
 * Movement to an open Lock, SIM, Battery or Panic incident and start corrupting
 * somebody else's work.
 *
 * The engine mints its `EventId` on the confirmed path, so a repeat that binds
 * to an active incident discards a minted identifier. What the contract requires
 * — and what the tests assert — is that no second row is ever persisted and the
 * active event's id is the one reported.
 */
class MotionIncidentBinder(
    private val repository: EventRepository,
    private val identityProvider: IdentityProvider = UuidIdentityProvider,
    private val engine: ProtectionStateEngine = DefaultProtectionStateEngine(),
    private val clock: Clock = Clock { Instant.now(JavaClock.systemUTC()) },
    private val activeEventScanLimit: Int = ACTIVE_EVENT_SCAN_LIMIT,
) {
    /** The incident the last call bound to or recorded; re-read from storage before it is trusted. */
    private var openIncident: SecurityEvent? = null

    @Synchronized
    fun confirm(signal: GuardSignal): MotionConfirmation {
        val evaluation = engine.evaluate(signal, clock, identityProvider)
        if (evaluation.confirmation.status != GuardConfirmationStatus.CONFIRMED) {
            return MotionConfirmation.NotConfirmed(evaluation.confirmation.reason)
        }
        val confirmed = evaluation.event
            ?: return MotionConfirmation.NotConfirmed("A confirmed Movement produced no event to persist")

        val at = clock.now()
        val active = rememberedIncident(at) ?: repository.listRecent(activeEventScanLimit)
            .firstOrNull { it.isOpenMotionIncident(at) }
        if (active != null) {
            openIncident = active
            return MotionConfirmation.BoundToActive(active)
        }

        val motion = confirmed.asMotionConfirmation()
        return when (val saved = repository.save(motion)) {
            is RepositoryResult.Applied -> {
                openIncident = motion
                MotionConfirmation.Recorded(motion)
            }
            is RepositoryResult.AlreadyExists ->
                MotionConfirmation.BoundToActive(repository.find(motion.id) ?: motion)
            is RepositoryResult.Conflict ->
                MotionConfirmation.Rejected("Motion event persistence conflicted with state ${saved.current}")
            RepositoryResult.NotFound ->
                MotionConfirmation.Rejected("Motion event persistence found no target row")
            is RepositoryResult.Rejected -> MotionConfirmation.Rejected(saved.reason)
        }
    }

    /**
     * The remembered incident, re-read by id so a state change made elsewhere is seen. Anything that
     * is gone, terminal, or past the window is forgotten and the caller falls back to the scan.
     */
    private fun rememberedIncident(at: Instant): SecurityEvent? {
        val remembered = openIncident ?: return null
        val current = repository.find(remembered.id)?.takeIf { it.isOpenMotionIncident(at) }
        if (current == null) openIncident = null
        return current
    }

    private fun SecurityEvent.isOpenMotionIncident(at: Instant): Boolean =
        type == SecurityEventType.MOTION_CONFIRMED_EVENT &&
            !state.isTerminal() &&
            Duration.between(createdAt, at) <= MOTION_REUSE_WINDOW

    /**
     * Tags the engine's event as Motion before the first write, so a crash
     * cannot leave a saved confirmed event that the next confirmation would
     * fail to recognize as an open Motion incident.
     */
    private fun SecurityEvent.asMotionConfirmation(): SecurityEvent =
        SecurityEvent.create(
            eventId = id,
            createdAt = createdAt,
            expiresAt = expiresAt,
            state = state,
            severity = severity,
            evidenceReferences = evidenceReferences,
            deliveryInformation = deliveryInformation,
            metadata = metadata,
            type = SecurityEventType.MOTION_CONFIRMED_EVENT,
        )

    private companion object {
        /** The active Motion incident is the newest one; history is bounded well below this. */
        const val ACTIVE_EVENT_SCAN_LIMIT = 100

        /**
         * The same ten minutes the owner approved for Panic (Q-K-8, A-28), applied to Motion for the
         * same reason. Named once so no call site can restate a different deadline; a Motion-specific
         * value is an owner decision and changes only here.
         */
        const val MOTION_REUSE_WINDOW_MINUTES: Long = 10L

        val MOTION_REUSE_WINDOW: Duration = Duration.ofMinutes(MOTION_REUSE_WINDOW_MINUTES)
    }
}
