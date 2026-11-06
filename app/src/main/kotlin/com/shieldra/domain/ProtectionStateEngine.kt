package com.shieldra.domain

import java.time.Instant

/** Pure deterministic engine; platform adapters are intentionally outside this class. */
class DefaultProtectionStateEngine : ProtectionStateEngine {

    @Synchronized
    override fun evaluate(
        signal: GuardSignal,
        clock: Clock,
        identityProvider: IdentityProvider,
    ): GuardEvaluation {
        if (!signal.isShapeValid()) {
            return GuardEvaluation(GuardConfirmation(GuardConfirmationStatus.NOT_A_SECURITY_EVENT))
        }

        return when (signal.kind) {
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
        val repeatedWithinWindow = motion.suspiciousOccurrences >= ApprovedGuardRules.motionOccurrencesToConfirm &&
            motion.suspiciousOccurrencesWithin != null &&
            motion.suspiciousOccurrencesWithin <= ApprovedGuardRules.motionRepetitionWindow
        val confirmed = motion.duration >= ApprovedGuardRules.motionStrongDuration || repeatedWithinWindow
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

}
