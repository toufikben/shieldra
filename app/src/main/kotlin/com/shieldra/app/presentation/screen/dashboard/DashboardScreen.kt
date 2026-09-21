package com.shieldra.app.presentation.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.domain.EmptyState
import com.shieldra.app.design.components.domain.EventCard
import com.shieldra.app.design.components.domain.GuardTile
import com.shieldra.app.design.components.domain.LiveStatusStrip
import com.shieldra.app.design.components.domain.ShieldCore
import com.shieldra.app.design.components.foundation.ShieldraAdSlot
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.model.DashboardUiModel
import com.shieldra.app.presentation.model.GuardUiModel
import com.shieldra.app.presentation.preview.PreviewData

@Composable
fun DashboardScreen(
    model: DashboardUiModel,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = spacing.xxxl),
    ) {
        item {
            ShieldCore(
                state = model.protectionState,
                lastCheckSeconds = model.lastCheckSeconds,
            )
        }

        item {
            LiveStatusStrip(
                lastCheckLabel = stringResource(R.string.seconds_ago, model.lastCheckSeconds),
                batteryPercent = model.batteryPercent,
                networkLabel = model.networkLabel,
                networkOk = model.networkOk,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        item {
            Spacer(Modifier.height(spacing.xxl))
            PanicQuickAction(onPanic = callbacks.onPanic)
        }

        item {
            Spacer(Modifier.height(spacing.xxl))
            SectionLabel(stringResource(R.string.dashboard_section_guards))
        }

        item {
            GuardsGrid(
                guards = model.guards,
                onGuardClick = callbacks.onGuardClick,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        item {
            Spacer(Modifier.height(spacing.xxl))
            SectionHeaderWithAction(
                label = stringResource(R.string.dashboard_section_recent),
                actionLabel = stringResource(R.string.dashboard_view_all),
                onAction = callbacks.onViewAllEvents,
            )
        }

        if (model.recentEvents.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_events_title),
                    message = stringResource(R.string.empty_events_message),
                    modifier = Modifier.padding(vertical = spacing.xl),
                )
            }
        } else {
            items(model.recentEvents) { event ->
                EventCard(
                    event = event,
                    onClick = { callbacks.onEventClick(event.id) },
                    modifier = Modifier.padding(horizontal = spacing.l, vertical = spacing.xs),
                )
            }
        }

        if (model.showAds) {
            item {
                Spacer(Modifier.height(spacing.xl))
                ShieldraAdSlot(modifier = Modifier.padding(horizontal = spacing.l))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val spacing = ShieldraTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = spacing.l,
            end = spacing.l,
            bottom = spacing.s,
        ),
    )
}

@Composable
private fun SectionHeaderWithAction(
    label: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val spacing = ShieldraTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, end = spacing.l, bottom = spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PanicQuickAction(onPanic: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.l)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(role = Role.Button, onClick = onPanic)
            .padding(horizontal = spacing.l, vertical = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.silent_panic),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.trigger_emergency_event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun GuardsGrid(
    guards: List<GuardUiModel>,
    onGuardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        guards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                row.forEach { guard ->
                    GuardTile(
                        guard = guard,
                        onClick = { onGuardClick(guard.kind.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Preview(name = "Dashboard Dark", showBackground = true)
@Composable
private fun DashboardDarkPreview() {
    ShieldraTheme(darkTheme = true) {
        DashboardScreen(
            model = PreviewData.dashboard,
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}

@Preview(name = "Dashboard Light", showBackground = true)
@Composable
private fun DashboardLightPreview() {
    ShieldraTheme(darkTheme = false) {
        DashboardScreen(
            model = PreviewData.dashboard,
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}
