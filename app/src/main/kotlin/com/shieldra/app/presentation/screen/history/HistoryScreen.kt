package com.shieldra.app.presentation.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.domain.EmptyState
import com.shieldra.app.design.components.domain.EventCard
import com.shieldra.app.design.components.foundation.ShieldraChip
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.HistoryFilterState
import com.shieldra.app.presentation.model.HistoryStatusFilter
import com.shieldra.app.presentation.model.HistoryTypeFilter
import com.shieldra.app.presentation.preview.PreviewData

@Composable
fun HistoryScreen(
    events: List<EventUiModel>,
    filterState: HistoryFilterState,
    callbacks: HistoryCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val filteredEvents = filterHistoryEvents(events, filterState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TypeFilterRow(
            current = filterState.type,
            onSelect = { newType ->
                callbacks.onFilterChange(
                    HistoryFilterStateChanged(newType, filterState.status)
                )
            },
        )
        StatusFilterRow(
            current = filterState.status,
            onSelect = { newStatus ->
                callbacks.onFilterChange(
                    HistoryFilterStateChanged(filterState.type, newStatus)
                )
            },
        )

        if (filteredEvents.isEmpty()) {
            EmptyState(
                title = stringResource(
                    if (events.isEmpty()) R.string.empty_history_no_events_title
                    else R.string.empty_history_title,
                ),
                message = stringResource(
                    if (events.isEmpty()) R.string.empty_history_no_events_message
                    else R.string.empty_history_message,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.l,
                    end = spacing.l,
                    top = spacing.s,
                    bottom = spacing.xxxl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                items(filteredEvents) { event ->
                    EventCard(
                        event = event,
                        onClick = { callbacks.onEventClick(event.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeFilterRow(
    current: HistoryTypeFilter,
    onSelect: (HistoryTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryTypeFilter.values().forEach { filter ->
            ShieldraChip(
                label = filter.displayLabel(),
                selected = filter == current,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    current: HistoryStatusFilter,
    onSelect: (HistoryStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryStatusFilter.values().forEach { filter ->
            ShieldraChip(
                label = filter.displayLabel(),
                selected = filter == current,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun HistoryTypeFilter.displayLabel(): String = stringResource(when (this) {
    HistoryTypeFilter.All -> R.string.filter_all
    HistoryTypeFilter.Lock -> R.string.filter_lock
    HistoryTypeFilter.Motion -> R.string.filter_motion
    HistoryTypeFilter.Sim -> R.string.filter_sim
    HistoryTypeFilter.Panic -> R.string.filter_panic
    HistoryTypeFilter.Battery -> R.string.filter_battery
})

@Composable
private fun HistoryStatusFilter.displayLabel(): String = stringResource(when (this) {
    HistoryStatusFilter.All -> R.string.filter_any_status
    HistoryStatusFilter.Delivered -> R.string.status_delivered
    HistoryStatusFilter.Deferred -> R.string.filter_deferred
    HistoryStatusFilter.Failed -> R.string.status_failed
})

@Preview(showBackground = true)
@Composable
private fun HistoryDarkPreview() {
    ShieldraTheme(darkTheme = true) {
        HistoryScreen(
            events = PreviewData.recentEvents,
            filterState = HistoryFilterState(),
            callbacks = HistoryCallbacks({}, {}),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryEmptyPreview() {
    ShieldraTheme(darkTheme = true) {
        HistoryScreen(
            events = emptyList(),
            filterState = HistoryFilterState(),
            callbacks = HistoryCallbacks({}, {}),
        )
    }
}
