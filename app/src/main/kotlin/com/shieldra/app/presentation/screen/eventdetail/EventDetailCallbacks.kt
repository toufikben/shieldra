package com.shieldra.app.presentation.screen.eventdetail

import androidx.compose.runtime.Immutable

@Immutable
data class EventDetailCallbacks(
    val onBack: () -> Unit,
    val onOpenInMaps: (Double, Double) -> Unit,
    val onDelete: () -> Unit,
    val onExport: () -> Unit,
)
