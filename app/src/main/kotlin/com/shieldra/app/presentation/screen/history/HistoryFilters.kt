package com.shieldra.app.presentation.screen.history

import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.HistoryFilterState
import com.shieldra.app.presentation.model.HistoryStatusFilter
import com.shieldra.app.presentation.model.HistoryTypeFilter

fun filterHistoryEvents(
    events: List<EventUiModel>,
    filterState: HistoryFilterState,
): List<EventUiModel> = events.filter { event ->
    val typeMatches = when (filterState.type) {
        HistoryTypeFilter.All -> true
        HistoryTypeFilter.Lock -> event.type == EventType.FailedUnlock
        HistoryTypeFilter.Motion -> event.type == EventType.Motion
        HistoryTypeFilter.Sim -> event.type == EventType.SimChange
        HistoryTypeFilter.Panic -> event.type == EventType.Panic
        HistoryTypeFilter.Battery -> event.type == EventType.Battery
    }
    val statusMatches = when (filterState.status) {
        HistoryStatusFilter.All -> true
        HistoryStatusFilter.Delivered -> event.deliveryStatus == DeliveryStatus.Delivered
        HistoryStatusFilter.Deferred -> event.deliveryStatus == DeliveryStatus.Deferred
        HistoryStatusFilter.Failed -> event.deliveryStatus == DeliveryStatus.Failed
    }
    typeMatches && statusMatches
}
