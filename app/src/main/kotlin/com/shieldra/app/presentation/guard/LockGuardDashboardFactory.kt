package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.DashboardUiModel
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardUiModel
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
): DashboardUiModel {
    val statuses = LockGuardDecision.guardStatuses(state)
    val inactiveSubtitle = stringResource(R.string.guard_subtitle_inactive)
    val motionSubtitle = stringResource(MotionMonitoringPresentation.subtitleRes(motionClaim))
    val guards = GuardKind.entries.map { kind ->
        val monitoring = kind == GuardKind.Motion
        GuardUiModel(
            kind = kind,
            title = stringResource(titleRes(kind)),
            subtitle = if (monitoring) motionSubtitle else inactiveSubtitle,
            status = if (monitoring) {
                MotionMonitoringPresentation.guardStatus(motionClaim)
            } else {
                statuses.getValue(kind)
            },
            enabled = false,
        )
    }
    return DashboardUiModel(
        protectionState = LockGuardDecision.protectionState(state),
        lastCheckSeconds = LockGuardDecision.lastCheckSeconds(state),
        guards = guards,
        recentEvents = recentEvents,
        batteryPercent = null,
        networkLabel = stringResource(R.string.status_network_unknown),
        networkOk = false,
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

