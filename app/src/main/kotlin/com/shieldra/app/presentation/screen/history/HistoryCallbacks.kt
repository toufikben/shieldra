package com.shieldra.app.presentation.screen.history

import androidx.compose.runtime.Immutable

@Immutable
data class HistoryCallbacks(
    val onEventClick: (String) -> Unit,
    val onFilterChange: (HistoryFilterStateChanged) -> Unit,
)

@Immutable
data class HistoryFilterStateChanged(
    val type: com.shieldra.app.presentation.model.HistoryTypeFilter,
    val status: com.shieldra.app.presentation.model.HistoryStatusFilter,
)
