package com.shieldra.detection

import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardObservation
import com.shieldra.domain.GuardSignal
import java.time.Instant

/**
 * Explicit capability outcomes for the future Android Lock adapter.
 * No outcome implies universal or continuous PIN observability.
 */
enum class LockGuardCapabilityState {
    AVAILABLE,
    NOT_DEVICE_ADMIN,
    UNSUPPORTED,
    PERMISSION_OR_PROVISIONING_REQUIRED,
    RUNTIME_ERROR,
}

sealed interface LockGuardBoundaryResult {
    data class VerifiedFailedPinCallback(
        val signal: GuardSignal,
    ) : LockGuardBoundaryResult {
        init {
            require(signal.kind == GuardKind.LOCK) {
                "Lock callbacks must produce Lock signals"
            }
            require(signal.observation == GuardObservation.FailedPinAttempt) {
                "Lock callbacks must produce failed-PIN observations"
            }
        }
    }

    data class Unavailable(
        val state: LockGuardCapabilityState,
        val reason: String,
    ) : LockGuardBoundaryResult {
        init {
            require(state != LockGuardCapabilityState.AVAILABLE) {
                "Available capability must produce a verified callback result"
            }
            require(reason.isNotBlank()) { "Unavailable capability requires a reason" }
        }
    }
}

/**
 * Framework-free boundary for an adapter that has genuinely received a
 * verified device-PIN failure callback. The adapter remains responsible for
 * proving that the callback came from an approved Android capability.
 */
object LockGuardBoundary {
    fun verifiedFailedPinCallback(
        capability: LockGuardCapabilityState,
        observedAt: Instant,
    ): LockGuardBoundaryResult = when (capability) {
        LockGuardCapabilityState.AVAILABLE -> LockGuardBoundaryResult.VerifiedFailedPinCallback(
            signal = GuardSignal(
                kind = GuardKind.LOCK,
                observedAt = observedAt,
                observation = GuardObservation.FailedPinAttempt,
            ),
        )
        else -> LockGuardBoundaryResult.Unavailable(
            state = capability,
            reason = "Verified Lock callback is unavailable for capability state $capability",
        )
    }
}

interface LockGuard

interface MotionGuard

interface SimGuard

interface BatteryGuard

interface PanicGuard
