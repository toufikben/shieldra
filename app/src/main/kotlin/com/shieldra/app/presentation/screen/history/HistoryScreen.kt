package com.shieldra.app.presentation.screen.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.domain.EmptyState
import com.shieldra.app.design.components.domain.EventCard
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.components.foundation.ShieldraChip
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.demo.DemoData
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.HistoryFilterState
import com.shieldra.app.presentation.model.HistoryStatusFilter
import com.shieldra.app.presentation.model.HistoryTypeFilter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The day group an event list row sits under. */
private sealed interface DayGroup {
    data object Today : DayGroup
    data object Yesterday : DayGroup
    data object Unknown : DayGroup
    data class Dated(val date: LocalDate) : DayGroup
}

private fun groupFor(event: EventUiModel, nowMs: Long, zone: ZoneId): DayGroup =
    when (HistoryStats.bucket(event.createdAtEpochMs, nowMs, zone)) {
        HistoryStats.DayBucket.TODAY -> DayGroup.Today
        HistoryStats.DayBucket.YESTERDAY -> DayGroup.Yesterday
        HistoryStats.DayBucket.EARLIER ->
            if (event.createdAtEpochMs <= 0L) DayGroup.Unknown
            else DayGroup.Dated(HistoryStats.dayOf(event.createdAtEpochMs, zone))
    }

@Composable
fun HistoryScreen(
    events: List<EventUiModel>,
    filterState: HistoryFilterState,
    callbacks: HistoryCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val filteredEvents = filterHistoryEvents(events, filterState)
    val zone = remember { ZoneId.systemDefault() }
    val nowMs = remember(events) { System.currentTimeMillis() }
    val week = remember(events) { HistoryStats.weekly(events.map { it.createdAtEpochMs }, nowMs, zone) }
    val groups = remember(filteredEvents) {
        filteredEvents.groupBy { groupFor(it, nowMs, zone) }.toList()
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.10f), Color.Transparent),
                    endY = 360f,
                ),
            ),
        contentPadding = PaddingValues(bottom = spacing.xxxl),
    ) {
        item {
            WeekChart(
                week = week,
                locale = locale,
                modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.m),
            )
        }
        item {
            TypeFilterRow(
                current = filterState.type,
                countOf = { typeCounts[it] ?: 0 },
                onSelect = { newType ->
                    callbacks.onFilterChange(HistoryFilterStateChanged(newType, filterState.status))
                },
            )
            StatusFilterRow(
                current = filterState.status,
                onSelect = { newStatus ->
                    callbacks.onFilterChange(HistoryFilterStateChanged(filterState.type, newStatus))
                },
            )
        }

        if (filteredEvents.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(
                        if (events.isEmpty()) R.string.empty_history_no_events_title
                        else R.string.empty_history_title,
                    ),
                    message = stringResource(
                        if (events.isEmpty()) R.string.empty_history_no_events_message
                        else R.string.empty_history_message,
                    ),
                    modifier = Modifier.padding(vertical = spacing.xxl),
                )
            }
        } else {
            groups.forEach { (group, groupEvents) ->
                item(key = headerKey(group)) {
                    Text(
                        text = when (group) {
                            DayGroup.Today -> stringResource(R.string.history_today)
                            DayGroup.Yesterday -> stringResource(R.string.history_yesterday)
                            DayGroup.Unknown -> stringResource(R.string.history_earlier)
                            is DayGroup.Dated -> group.date.format(dateFormat)
                        } + "  ·  " + groupEvents.size,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = spacing.l, end = spacing.l, top = spacing.l, bottom = spacing.s),
                    )
                }
                items(groupEvents, key = { it.id }) { event ->
                    EventCard(
                        event = if (event.createdAtEpochMs > 0L) {
                            // Under a day header the clock time is the useful part; today keeps the age too.
                            val clock = java.time.Instant.ofEpochMilli(event.createdAtEpochMs).atZone(zone).format(timeFormat)
                            event.copy(timestampLabel = if (group == DayGroup.Today) "$clock  ·  ${event.timestampLabel}" else clock)
                        } else {
                            event
                        },
                        onClick = { callbacks.onEventClick(event.id) },
                        modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.xs),
                    )
                }
            }
        }
    }
}

/** Seven columns, oldest first; bar heights are the real per-day event counts. */
@Composable
private fun WeekChart(
    week: List<HistoryStats.DayCount>,
    locale: java.util.Locale,
    modifier: Modifier = Modifier,
) {
    val accent = ShieldraTheme.semantic.protection
    val total = week.sumOf { it.count }
    val maxCount = (week.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.history_week_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.history_week_total, total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { day ->
                val fraction by animateFloatAsState(
                    targetValue = day.count.toFloat() / maxCount,
                    label = "history_bar",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height((6 + 56 * fraction).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (day.isToday) Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f)))
                                else Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
                                    ),
                                ),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = day.weekday.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeFilterRow(
    current: HistoryTypeFilter,
    countOf: (HistoryTypeFilter) -> Int,
    onSelect: (HistoryTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryTypeFilter.entries.forEach { filter ->
            val count = countOf(filter)
            ShieldraChip(
                label = if (count > 0) "${filter.displayLabel()} $count" else filter.displayLabel(),
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
        HistoryStatusFilter.entries.forEach { filter ->
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
            events = DemoData.recentEvents(),
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

@Preview(showBackground = true)
@Composable
private fun HistoryNoMatchesPreview() {
    ShieldraTheme(darkTheme = true) {
        HistoryScreen(
            events = DemoData.recentEvents(),
            filterState = HistoryFilterState(HistoryTypeFilter.Panic, HistoryStatusFilter.All),
            callbacks = HistoryCallbacks({}, {}),
        )
    }
}

private fun headerKey(group: DayGroup): String = when (group) {
    DayGroup.Today -> "header_today"
    DayGroup.Yesterday -> "header_yesterday"
    DayGroup.Unknown -> "header_unknown"
    is DayGroup.Dated -> "header_${group.date}"
}
