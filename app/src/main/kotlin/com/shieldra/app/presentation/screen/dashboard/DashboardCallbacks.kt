package com.shieldra.app.presentation.screen.dashboard

import androidx.compose.runtime.Immutable

/**
 * The actions the dashboard really offers.
 *
 * `onEventClick` and `onViewAllEvents` were removed with the Recent list in `7184c2e`: History owns
 * the event list now, and no Composable on this screen raises either action. Keeping them here would
 * leave two wired callbacks pointing at navigation nothing can reach.
 */
@Immutable
data class DashboardCallbacks(
    val onGuardClick: (String) -> Unit,
    val onPanic: () -> Unit,
    /** Brings Motion monitoring up or down; the state it leaves behind is read back from the service. */
    val onMotionMonitoringToggle: () -> Unit = {},
    /** A plain tap on Panic, which only explains that Panic needs a press-and-hold. */
    val onPanicTapHint: () -> Unit = {},
    /** Opens the Pro screen from the dashboard top bar (A-40). */
    val onOpenPro: () -> Unit = {},
    /** Restarts the periodic signal's cycle from now (A-33). Weakening, so the caller verifies first. */
    val onPeriodicSignalReset: () -> Unit = {},
)
