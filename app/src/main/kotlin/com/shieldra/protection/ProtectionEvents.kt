package com.shieldra.protection

import com.shieldra.domain.Clock
import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import com.shieldra.domain.EvidenceReference
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.IdentityProvider
import com.shieldra.domain.isTerminal
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import com.shieldra.domain.UuidIdentityProvider
import java.time.Clock as JavaClock
import java.time.Duration
import java.time.Instant

/**
 * Records protection events and attaches the evidence each capture produced (A-33/A-34).
 *
 * Two triggers group: repeated wrong unlocks inside the reuse window join one open Lock incident
 * (`LockGuardReceiver` raises `LOCK_CONFIRMED_EVENT` once per burst, and a second burst inside the
 * window belongs to the same incident), and a SIM guard firing again inside the window joins its own
 * open SIM incident. Countdown, battery, tamper, motion and panic each stand alone. The binding never
 * crosses types. Design B (2026-11-06) had removed the Lock grouping with the guard; `bb0455f` brought
 * the guard back and the grouping with it.
 */
class ProtectionEvents(
    private val repository: EventRepository,
    private val clock: Clock = Clock { Instant.now(JavaClock.systemUTC()) },
    private val identity: IdentityProvider = UuidIdentityProvider,
    private val reuseWindow: Duration = Duration.ofMinutes(10),
) {
    /** The event this capture belongs to, and whether it is a brand-new incident (worth announcing). */
    data class Binding(val event: SecurityEvent, val isNew: Boolean)

    @Synchronized
    fun openOrBind(type: SecurityEventType, grouped: Boolean, severity: GuardSeverity): Binding? {
        val at = clock.now()
        if (grouped) {
            val open = repository.listRecent(ACTIVE_SCAN_LIMIT).firstOrNull {
                it.type == type && !it.state.isTerminal() && Duration.between(it.createdAt, at) <= reuseWindow
            }
            if (open != null) return Binding(open, isNew = false)
        }
        val event = SecurityEvent.create(
            eventId = identity.nextEventId(),
            createdAt = at,
            severity = severity,
            type = type,
        )
        return when (repository.save(event)) {
            is RepositoryResult.Applied -> Binding(event, isNew = true)
            // A colliding id is reusable only when the database really contains that incident.
            is RepositoryResult.AlreadyExists -> repository.find(event.id)?.let { Binding(it, isNew = false) }
            else -> null
        }
    }

    /** Stores the evidence id against the event; a failure is non-fatal to the alert. */
    fun attachEvidence(eventId: EventId, evidenceId: String) {
        runCatching { repository.recordEvidence(eventId, EvidenceReference(evidenceId)) }
    }

    private companion object {
        const val ACTIVE_SCAN_LIMIT = 100
    }
}
