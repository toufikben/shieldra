package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.DashboardUiModel
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.app.presentation.model.GuardUiModel
import com.shieldra.app.presentation.model.NetworkStatus
import com.shieldra.app.presentation.model.ProtectionVisualState
import com.shieldra.runtime.MonitoringClaim

/**
 * Builds the dashboard the app may honestly show, straight from the pure
 * [LockGuardDecision]. It never invents a "Protected" state, an active guard,
 * a battery reading, a network reading, or ads — none of those facts exist
 * yet, regardless of the storage gate. Only [recentEvents] is real, and it is
 * whatever the persisted reader actually returned. [motionClaim] is real too,
 * and it is the only thing that can make the Motion guard look active: it comes
 * from the monitoring service's own state, never from a press.
 */
@Composable
fun lockGuardDashboard(
    state: LockGuardState,
    recentEvents: List<EventUiModel> = emptyList(),
    motionClaim: MonitoringClaim = MonitoringClaim.NoExecution,
    // A-33/A-34: when the owner has armed protection, the guards they switched on are really running
    // behind the protection service, so they render Active and the shield reads Protected. Nothing
    // here is fabricated: an unarmed build still goes through the honest LockGuardDecision path, and
    // the not-active case still maps to Disabled.
    protectionArmed: Boolean = false,
    lockOn: Boolean = false,
    simOn: Boolean = false,
    batteryOn: Boolean = false,
): DashboardUiModel {
    val statuses = LockGuardDecision.guardStatuses(state)
    val inactiveSubtitle = stringResource(R.string.guard_subtitle_inactive)
    val activeSubtitle = stringResource(R.string.guard_subtitle_active)
    val motionSubtitle = stringResource(MotionMonitoringPresentation.subtitleRes(motionClaim))
    val armedGuards: Map<GuardKind, Boolean> = mapOf(
        GuardKind.Lock to (protectionArmed && lockOn),
        GuardKind.Sim to (protectionArmed && simOn),
        GuardKind.Battery to (protectionArmed && batteryOn),
    )
    val guards = GuardKind.entries.map { kind ->
        val monitoring = kind == GuardKind.Motion
        val armedActive = armedGuards[kind] == true
        GuardUiModel(
            kind = kind,
            title = stringResource(titleRes(kind)),
            subtitle = when {
                monitoring -> motionSubtitle
                armedActive -> activeSubtitle
                else -> inactiveSubtitle
            },
            status = when {
                monitoring -> MotionMonitoringPresentation.guardStatus(motionClaim)
                armedActive -> GuardStatus.Active
                else -> statuses.getValue(kind)
            },
            enabled = false,
        )
    }
    val protectionState =
        if (protectionArmed) ProtectionVisualState.Protected else LockGuardDecision.protectionState(state)
    return DashboardUiModel(
        protectionState = protectionState,
        lastCheckSeconds = LockGuardDecision.lastCheckSeconds(state),
        guards = guards,
        recentEvents = recentEvents,
        batteryPercent = null,
        networkLabel = stringResource(R.string.status_network_unknown),
        networkStatus = NetworkStatus.Unknown,
        showAds = false,
        storageInstalled = state == LockGuardState.StorageIdle,
    )
}

@StringRes
private fun titleRes(kind: GuardKind): Int = when (kind) {
    GuardKind.Lock -> R.string.demo_lock_guard
    GuardKind.Motion -> R.string.demo_motion_guard
    GuardKind.Sim -> R.string.demo_sim_guard
    GuardKind.Panic -> R.string.demo_panic
    GuardKind.Battery -> R.string.event_battery
}

