package com.shieldra.domain

import java.time.Duration
import java.time.Instant

/** Pure deterministic engine; platform adapters are intentionally outside this class. */
class DefaultProtectionStateEngine : ProtectionStateEngine {
    private val lockFailures = mutableListOf<Instant>()
    private var lastLockConfirmationAt: Instant? = null

    override fun evaluate(
        signal: GuardSignal,
        clock: Clock,
        identityProvider: IdentityProvider,
    ): GuardEvaluation {
        val now = clock.now()
        if (!signal.isShapeValid()) {
            return GuardEvaluation(GuardConfirmation(GuardConfirmationStatus.NOT_A_SECURITY_EVENT))
        }

        return when (signal.kind) {
            GuardKind.LOCK -> evaluateLock(signal, now, identityProvider)
            GuardKind.MOTION -> evaluateMotion(signal, identityProvider)
            GuardKind.SIM -> confirmed(signal, GuardSeverity.HIGH, identityProvider)
            GuardKind.BATTERY -> GuardEvaluation(
                GuardConfirmation(
                    status = GuardConfirmationStatus.NOT_A_SECURITY_EVENT,
                    reason = "Battery percentage or an undefined power anomaly is not a confirmed event",
                ),
            )
            GuardKind.PANIC -> confirmed(signal, GuardSeverity.CRITICAL, identityProvider)
        }
    }

    private fun evaluateLock(
        signal: GuardSignal,
        now: Instant,
        identityProvider: IdentityProvider,
    ): GuardEvaluation {
        val lastConfirmed = lastLockConfirmationAt
        if (lastConfirmed != null && elapsed(now, lastConfirmed) < ApprovedGuardRules.lockCooldown) {
            return GuardEvaluation(
                GuardConfirmation(
                    status = GuardConfirmationStatus.SIGNAL_ONLY,
                    reason = "Same Lock attack sequence is inside the approved cooldown",
                ),
            )
        }

        lockFailures.removeAll { elapsed(now, it) >= ApprovedGuardRules.lockCounterResetWindow }
        lockFailures += signal.observedAt
        val recentFailures = lockFailures.count {
            elapsed(signal.observedAt, it) <= ApprovedGuardRules.lockConfirmationWindow
        }
        if (recentFailures < ApprovedGuardRules.lockAttemptsToConfirm) {
            return GuardEvaluation(GuardConfirmation(GuardConfirmationStatus.SIGNAL_ONLY))
        }

        lockFailures.clear()
        lastLockConfirmationAt = now
        return confirmed(signal, GuardSeverity.HIGH, identityProvider)
    }

    private fun evaluateMotion(
        signal: GuardSignal,
        identityProvider: IdentityProvider,
    ): GuardEvaluation {
        val motion = signal.observation as GuardObservation.Motion
        if (motion.sensorQuality != SensorQuality.VALID) {
            return GuardEvaluation(
                GuardConfirmation(
                    status = GuardConfirmationStatus.SIGNAL_ONLY,
                    reason = "Motion sensor quality is not valid",
                ),
            )
        }
        val confirmed = motion.duration >= ApprovedGuardRules.motionStrongDuration ||
            motion.suspiciousOccurrences >= ApprovedGuardRules.motionOccurrencesToConfirm
        return if (confirmed) {
            confirmed(signal, GuardSeverity.HIGH, identityProvider)
        } else {
            GuardEvaluation(GuardConfirmation(GuardConfirmationStatus.SIGNAL_ONLY))
        }
    }

    private fun confirmed(
        signal: GuardSignal,
        severity: GuardSeverity,
        identityProvider: IdentityProvider,
    ): GuardEvaluation = GuardEvaluation(
        confirmation = GuardConfirmation(GuardConfirmationStatus.CONFIRMED, severity),
        event = SecurityEvent.create(
            eventId = identityProvider.nextEventId(),
            createdAt = signal.observedAt,
            severity = severity,
        ),
    )

    private fun elapsed(later: Instant, earlier: Instant): Duration =
        if (later.isBefore(earlier)) Duration.ZERO else Duration.between(earlier, later)
}
