package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.app.presentation.model.ProtectionVisualState

/**
 * The only protection states SHIELDRA can honestly project today.
 *
 * There is no live sensor detection and no background monitoring service yet —
 * both are gated behind the phase 5 / G3 owner policy — so the app must never
 * render a device as "Protected" or a guard as actively "Active". The single
 * honest distinction available now is whether local storage is installed at all.
 */
sealed interface LockGuardState {
    /** Storage gate is off (the shipped default): nothing is persisted or running. */
    data object NotActive : LockGuardState

    /** Storage is installed, but monitoring is not configured: an honest idle state. */
    data object StorageIdle : LockGuardState
}

/**
 * Pure guard projection. It is deliberately free of Android and Compose so the
 * no-overclaiming guarantee is unit-testable. It never produces
 * [ProtectionVisualState.Protected] / [ProtectionVisualState.Suspicious] /
 * [ProtectionVisualState.SecurityEvent] and never marks a guard
 * [GuardStatus.Active] / [GuardStatus.Signal], because no detector exists to
 * justify those claims. `GuardStatus.Disabled` for every guard is intentional:
 * the guard categories are known, but none is actually running yet.
 */
object LockGuardDecision {
    fun decide(storageInstalled: Boolean): LockGuardState =
        if (storageInstalled) LockGuardState.StorageIdle else LockGuardState.NotActive

    fun protectionState(state: LockGuardState): ProtectionVisualState = when (state) {
        LockGuardState.NotActive -> ProtectionVisualState.Disabled
        LockGuardState.StorageIdle -> ProtectionVisualState.Attention
    }

    fun guardStatuses(state: LockGuardState): Map<GuardKind, GuardStatus> =
        GuardKind.entries.associateWith { GuardStatus.Disabled }
}
