package com.shieldra.domain

import java.time.Duration
import java.time.Instant

/**
 * Approved product-level Guard categories; platform adapters are intentionally absent.
 *
 * Design B (2026-11-06): there is no LOCK category. Its only sensor was the device-admin
 * `onPasswordFailed` callback, an authority the app may not hold, so the guard was removed with
 * its boundary rather than kept as a category nothing can ever feed.
 */
enum class GuardKind {
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
    data class Motion(
        val duration: Duration,
        val suspiciousOccurrences: Int,
        val sensorQuality: SensorQuality,
        val suspiciousOccurrencesWithin: Duration? = null,
    ) : GuardObservation {
        init {
            require(!duration.isNegative) { "Motion duration cannot be negative" }
            require(suspiciousOccurrences >= 0) { "Motion occurrences cannot be negative" }
            require(suspiciousOccurrencesWithin?.isNegative != true) {
                "Motion occurrence window cannot be negative"
            }
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
    val motionStrongDuration: Duration = Duration.ofSeconds(3)
    val motionRepetitionWindow: Duration = Duration.ofSeconds(10)
    const val motionOccurrencesToConfirm: Int = 2
}

fun GuardSignal.isShapeValid(): Boolean = when (kind) {
    GuardKind.MOTION -> observation is GuardObservation.Motion
    GuardKind.SIM -> observation is GuardObservation.SimSubscriptionChanged
    GuardKind.BATTERY -> observation is GuardObservation.BatteryPowerState
    GuardKind.PANIC -> observation is GuardObservation.PanicActivated
}
