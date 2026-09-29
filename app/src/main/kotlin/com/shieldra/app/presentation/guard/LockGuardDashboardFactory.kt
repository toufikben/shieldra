package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.DashboardUiModel
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardUiModel

/**
 * Builds the dashboard the app may honestly show, straight from the pure
 * [LockGuardDecision]. It never invents a "Protected" state, an active guard,
 * a battery reading, a network reading, or ads — none of those facts exist yet,
 * because detection and monitoring (phase 5 / G3) are not implemented and the
 * storage gate is off by default. Only [recentEvents] is real, and it is
 * whatever the persisted reader actually returned.
 */
@Composable
fun lockGuardDashboard(
    state: LockGuardState,
    recentEvents: List<EventUiModel> = emptyList(),
): DashboardUiModel {
    val statuses = LockGuardDecision.guardStatuses(state)
    val inactiveSubtitle = stringResource(R.string.guard_subtitle_inactive)
    val guards = GuardKind.entries.map { kind ->
        GuardUiModel(
            kind = kind,
            title = stringResource(titleRes(kind)),
            subtitle = inactiveSubtitle,
            status = statuses.getValue(kind),
            enabled = false,
        )
    }
    return DashboardUiModel(
        protectionState = LockGuardDecision.protectionState(state),
        lastCheckSeconds = 0L,
        guards = guards,
        recentEvents = recentEvents,
        batteryPercent = null,
        networkLabel = stringResource(R.string.status_network_unknown),
        networkOk = false,
        showAds = false,
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

