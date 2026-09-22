package com.shieldra.domain

import java.time.Duration
import java.time.Instant

/** Approved product-level Guard categories; platform adapters are intentionally absent. */
enum class GuardKind {
    LOCK,
    MOTION,
    SIM,
    BATTERY,
    PANIC,
}

enum class GuardSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class SensorQuality {
    VALID,
    UNAVAILABLE,
    NOISY,
}

sealed interface GuardObservation {
    data object FailedPinAttempt : GuardObservation

    data class Motion(
        val duration: Duration,
        val suspiciousOccurrences: Int,
        val sensorQuality: SensorQuality,
    ) : GuardObservation {
        init {
            require(!duration.isNegative) { "Motion duration cannot be negative" }
            require(suspiciousOccurrences >= 0) { "Motion occurrences cannot be negative" }
        }
    }

    data object SimSubscriptionChanged : GuardObservation
    data class BatteryPowerState(val abnormal: Boolean) : GuardObservation
    data object PanicActivated : GuardObservation
}

data class GuardSignal(
    val kind: GuardKind,
    val observedAt: Instant,
    val observation: GuardObservation,
)

enum class GuardConfirmationStatus {
    SIGNAL_ONLY,
    CONFIRMED,
    NOT_A_SECURITY_EVENT,
}

data class GuardConfirmation(
    val status: GuardConfirmationStatus,
    val severity: GuardSeverity? = null,
    val reason: String? = null,
) {
    init {
        if (status == GuardConfirmationStatus.CONFIRMED) {
            require(severity != null) { "Confirmed guards require severity" }
        }
    }
}

/** Approved numeric rule values only. Unknown platform and response policies remain outside this object. */
object ApprovedGuardRules {
    val lockConfirmationWindow: Duration = Duration.ofMinutes(2)
    val lockCounterResetWindow: Duration = Duration.ofMinutes(10)
    val lockCooldown: Duration = Duration.ofMinutes(5)
    val motionStrongDuration: Duration = Duration.ofSeconds(3)
    val motionRepetitionWindow: Duration = Duration.ofSeconds(10)
    const val lockAttemptsToConfirm: Int = 2
    const val motionOccurrencesToConfirm: Int = 2
}

fun GuardSignal.isShapeValid(): Boolean = when (kind) {
    GuardKind.LOCK -> observation is GuardObservation.FailedPinAttempt
    GuardKind.MOTION -> observation is GuardObservation.Motion
    GuardKind.SIM -> observation is GuardObservation.SimSubscriptionChanged
    GuardKind.BATTERY -> observation is GuardObservation.BatteryPowerState
    GuardKind.PANIC -> observation is GuardObservation.PanicActivated
}
