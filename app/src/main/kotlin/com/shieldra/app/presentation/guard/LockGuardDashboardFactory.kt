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
import com.shieldra.protection.CountdownDeadline
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
    // P3-3: the armed flag is a stored boolean; the guards are a running service. The shield may only
    // read Protected when the service really is live, because the window between the two is real — a
    // phone that rebooted while armed is locked, and no boot receiver may start the camera-type service
    // before the first unlock.
    guardsRunning: Boolean = false,
    simOn: Boolean = false,
    batteryOn: Boolean = false,
    // A-38: the phone's own readings. Each defaults to "not measured", so a caller that has no reading
    // still renders the honest unmeasured state; only a real reading can replace it.
    batteryPercent: Int? = null,
    networkStatus: NetworkStatus = NetworkStatus.Unknown,
    networkLabel: String? = null,
    measuredLastCheckSeconds: Long? = null,
    protectionScore: Int? = null,
    proActive: Boolean = false,
    totalEvents: Int? = null,
    // A-33: the periodic signal's own stored cycle. Nothing here derives it from a press or from the
    // interval setting — only the deadline the scheduler actually armed can make the card appear.
    periodicSignal: CountdownDeadline? = null,
    periodicSignalIntervalMs: Long = 0L,
): DashboardUiModel {
    val statuses = LockGuardDecision.guardStatuses(state)
    val readiness = ArmedReadiness.decide(armed = protectionArmed, guardsRunning = guardsRunning)
    val inactiveSubtitle = stringResource(R.string.guard_subtitle_inactive)
    val activeSubtitle = stringResource(R.string.guard_subtitle_active)
    val motionSubtitle = stringResource(MotionMonitoringPresentation.subtitleRes(motionClaim))
    // A guard is only "active" when the service behind it is live: the switch alone is an intention.
    val serviceLive = readiness == ArmedReadiness.ArmedAndRunning
    val armedGuards: Map<GuardKind, Boolean> = mapOf(
        GuardKind.Sim to (serviceLive && simOn),
        GuardKind.Battery to (serviceLive && batteryOn),
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
    val protectionState = readiness.protectionVisualState()
    return DashboardUiModel(
        protectionState = protectionState,
        // P3-3: the sentence the waiting window owes the owner, or null when the disclosure line
        // already says everything true. Read here so the screen cannot invent its own wording.
        armedReadinessNote = readiness.noteRes?.let { stringResource(it) },
        lastCheckSeconds = measuredLastCheckSeconds ?: LockGuardDecision.lastCheckSeconds(state),
        guards = guards,
        recentEvents = recentEvents,
        batteryPercent = batteryPercent,
        networkLabel = networkLabel ?: stringResource(R.string.status_network_unknown),
        networkStatus = networkStatus,
        showAds = false,
        storageInstalled = state == LockGuardState.StorageIdle,
        protectionScore = protectionScore,
        proActive = proActive,
        totalEvents = totalEvents,
        periodicSignal = periodicSignal,
        periodicSignalIntervalMs = periodicSignalIntervalMs,
    )
}

@StringRes
private fun titleRes(kind: GuardKind): Int = when (kind) {
    GuardKind.Motion -> R.string.demo_motion_guard
    GuardKind.Sim -> R.string.demo_sim_guard
    GuardKind.Panic -> R.string.demo_panic
    GuardKind.Battery -> R.string.event_battery
}

