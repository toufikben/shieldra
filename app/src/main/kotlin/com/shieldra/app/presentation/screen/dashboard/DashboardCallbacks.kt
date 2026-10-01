package com.shieldra.app.presentation.screen.dashboard

import androidx.compose.runtime.Immutable

@Immutable
data class DashboardCallbacks(
    val onEventClick: (String) -> Unit,
    val onGuardClick: (String) -> Unit,
    val onPanic: () -> Unit,
    val onViewAllEvents: () -> Unit,
    /** Brings Motion monitoring up or down; the state it leaves behind is read back from the service. */
    val onMotionMonitoringToggle: () -> Unit = {},
)
