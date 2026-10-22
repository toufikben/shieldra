package com.shieldra.app.presentation.screen.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.settings.SettingsContent
import com.shieldra.app.presentation.settings.disclosureLabelRes
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
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.components.foundation.ShieldraAdSlot
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.presentation.model.DashboardUiModel
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.app.presentation.model.GuardUiModel
import com.shieldra.app.presentation.demo.DemoData

@Composable
fun DashboardScreen(
    model: DashboardUiModel,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val accent = ShieldraTheme.semantic.protection
    // Read off the Motion tile, which the dashboard factory built from the monitoring service's own
    // published claim. The disclosure line and the Start/Stop button follow this one reading so
    // neither can disagree with the tile about whether Motion is monitoring.
    val motionMonitoring = model.guards.firstOrNull { it.kind == GuardKind.Motion }?.status == GuardStatus.Active

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Premium ambient mesh: a teal wash at the top that fades into the base background.
            .background(
                Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    endY = 720f,
                ),
            ),
        contentPadding = PaddingValues(bottom = spacing.xxxl),
    ) {
        item {
            // Premium hero: a slow ring of orbiting dots and a tinted glow behind the live shield.
            val orbit = rememberInfiniteTransition(label = "hero_orbit")
            val angle by orbit.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
                label = "hero_angle",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .graphicsLayer { rotationZ = angle },
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).size(10.dp)
                            .clip(CircleShape).background(accent),
                    )
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(30.dp).size(8.dp)
                            .clip(CircleShape).background(accent.copy(alpha = 0.8f)),
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(30.dp).size(7.dp)
                            .clip(CircleShape).background(accent.copy(alpha = 0.6f)),
                    )
                }
                ShieldCore(
                    state = model.protectionState,
                    lastCheckSeconds = model.lastCheckSeconds,
                )
            }
        }

        item {
            Text(
                text = stringResource(
                    disclosureLabelRes(SettingsContent.dashboardMode(model.storageInstalled, motionMonitoring = motionMonitoring)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        item {
            LiveStatusStrip(
                lastCheckLabel = model.lastCheckSeconds
                    ?.let { stringResource(R.string.seconds_ago, it) }
                    ?: stringResource(R.string.last_check_none),
                batteryPercent = model.batteryPercent,
                networkLabel = model.networkLabel,
                networkStatus = model.networkStatus,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        item {
            Spacer(Modifier.height(spacing.xxl))
            PanicQuickAction(onPanic = callbacks.onPanic, onTapHint = callbacks.onPanicTapHint)
        }

        item {
            Spacer(Modifier.height(spacing.xxl))
            SectionLabel(stringResource(R.string.dashboard_section_guards))
        }

        item {
            GlassCard(modifier = Modifier.padding(horizontal = spacing.l)) {
                GuardsGrid(
                    guards = model.guards,
                    onGuardClick = callbacks.onGuardClick,
                )
            }
        }

        item {
            Spacer(Modifier.height(spacing.l))
            // The button follows the tile instead of reading the claim itself, so the two can never
            // disagree about whether Motion is monitoring.
            MotionMonitoringAction(
                monitoring = motionMonitoring,
                onToggle = callbacks.onMotionMonitoringToggle,
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
            items(model.recentEvents, key = { it.id }) { event ->
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

/**
 * Panic fires on press-and-hold only. A single tap in a pocket or a stray touch used to record an
 * emergency; a tap now explains the gesture instead, and a confirmed hold answers with a haptic so
 * the owner knows it worked without looking at the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanicQuickAction(onPanic: () -> Unit, onTapHint: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    val haptics = LocalHapticFeedback.current
    val holdLabel = stringResource(R.string.panic_hold_action_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.l)
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .combinedClickable(
                role = Role.Button,
                onClick = onTapHint,
                onLongClickLabel = holdLabel,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPanic()
                },
            )
            .defaultMinSize(minHeight = 64.dp)
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
                text = stringResource(R.string.panic_hold_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The owner's Start/Stop control for Motion monitoring. It asks for the transition and nothing more:
 * whether the label says Start or Stop is derived from the guard tile above, which is itself derived
 * from the state the service publishes, so a press can never make the screen claim monitoring.
 */
@Composable
private fun MotionMonitoringAction(
    monitoring: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Text(
        text = stringResource(
            if (monitoring) R.string.motion_monitoring_stop else R.string.motion_monitoring_start,
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = spacing.l, vertical = spacing.m),
    )
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
            model = DemoData.dashboard(),
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}

@Preview(name = "Dashboard Light", showBackground = true)
@Composable
private fun DashboardLightPreview() {
    ShieldraTheme(darkTheme = false) {
        DashboardScreen(
            model = DemoData.dashboard(),
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}

@Preview(name = "Dashboard Empty", showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    ShieldraTheme(darkTheme = true) {
        DashboardScreen(
            model = DemoData.dashboardEmpty(),
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}

@Preview(name = "Dashboard Suspicious", showBackground = true)
@Composable
private fun DashboardSuspiciousPreview() {
    ShieldraTheme(darkTheme = true) {
        DashboardScreen(
            model = DemoData.dashboardSuspicious(),
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}

@Preview(name = "Dashboard Disabled", showBackground = true)
@Composable
private fun DashboardDisabledPreview() {
    ShieldraTheme(darkTheme = true) {
        DashboardScreen(
            model = DemoData.dashboardDisabled(),
            callbacks = DashboardCallbacks({}, {}, {}, {}),
        )
    }
}
