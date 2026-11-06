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
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.SystemClock
import kotlinx.coroutines.delay
import com.shieldra.protection.CountdownDeadline
import com.shieldra.protection.RemainingTime
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.l, end = spacing.s, top = spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Shieldra",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProPill(active = model.proActive, onClick = callbacks.onOpenPro)
            }
        }

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
                // The ring is centred on the shield (ShieldCore's shield sits in its upper area), and
                // larger than it, so the dots orbit around the shield rather than across the label.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = spacing.m + 2.dp)
                        .size(224.dp)
                        .graphicsLayer { rotationZ = angle },
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).size(13.dp)
                            .clip(CircleShape).background(accent),
                    )
                    Box(
                        Modifier.align(Alignment.CenterStart).size(10.dp)
                            .clip(CircleShape).background(accent.copy(alpha = 0.85f)),
                    )
                    Box(
                        Modifier.align(Alignment.CenterEnd).size(9.dp)
                            .clip(CircleShape).background(accent.copy(alpha = 0.7f)),
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).size(8.dp)
                            .clip(CircleShape).background(accent.copy(alpha = 0.55f)),
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
                lastCheckLabel = lastCheckAgo(model.lastCheckSeconds),
                batteryPercent = model.batteryPercent,
                networkLabel = model.networkLabel,
                networkStatus = model.networkStatus,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        item {
            Spacer(Modifier.height(spacing.l))
            val activeGuards = model.guards.count { it.status == GuardStatus.Active }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.l),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                StatTile(stringResource(R.string.stat_guards), "$activeGuards/${model.guards.size}", Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_protection), model.protectionScore?.let { "$it" } ?: "—", Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_events), "${model.totalEvents ?: model.recentEvents.size}", Modifier.weight(1f))
            }
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
            Spacer(Modifier.height(spacing.m))
            PanicQuickAction(
                onPanic = callbacks.onPanic,
                onTapHint = callbacks.onPanicTapHint,
                modifier = Modifier.padding(horizontal = spacing.l),
            )
        }

        // The periodic signal's cycle is the one running thing on this screen whose deadline the owner
        // can move, so it gets its own card beside Panic — and only when a cycle really is armed.
        item {
            val cycle = model.periodicSignal
            if (cycle != null) {
                Spacer(Modifier.height(spacing.m))
                PeriodicSignalAction(
                    deadline = cycle,
                    intervalMs = model.periodicSignalIntervalMs,
                    onReset = callbacks.onPeriodicSignalReset,
                    modifier = Modifier.padding(horizontal = spacing.l),
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

/** The strip's own label already says "Last check", so it carries the bare age. */
@Composable
private fun lastCheckAgo(seconds: Long?): String = when {
    seconds == null -> stringResource(R.string.last_check_none)
    seconds < 60 -> stringResource(R.string.ago_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.ago_minutes, seconds / 60)
    else -> stringResource(R.string.ago_hours, seconds / 3600)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, cornerRadius = 16.dp, contentPadding = 12.dp) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

/**
 * A premium, breathing action card: a tinted gradient with a pulsing border and an expanding halo
 * behind the icon. [pulsing] only drives the animation — what the card says and does comes from the
 * caller, so a pulse never claims more than the state behind it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PulseActionCard(
    accent: Color,
    icon: ImageVector,
    title: String,
    hint: String,
    pulsing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val spacing = ShieldraTheme.spacing
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "pulse_card")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pulse_phase",
    )
    val p = if (pulsing) phase else 0f
    val borderAlpha = if (pulsing) 0.25f + 0.40f * (1f - p) else 0.18f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = borderAlpha), ShieldraCardShape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            )
            .defaultMinSize(minHeight = 76.dp)
            .padding(horizontal = spacing.l, vertical = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (pulsing) {
                Box(
                    Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            val s = 0.7f + 0.6f * p
                            scaleX = s
                            scaleY = s
                            alpha = 1f - p
                        }
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.35f)),
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.size(spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Panic fires on press-and-hold only. A single tap in a pocket or a stray touch used to record an
 * emergency; a tap now explains the gesture instead, and a confirmed hold answers with a haptic so
 * the owner knows it worked without looking at the screen.
 */
@Composable
private fun PanicQuickAction(onPanic: () -> Unit, onTapHint: () -> Unit, modifier: Modifier = Modifier) {
    PulseActionCard(
        accent = ShieldraTheme.semantic.securityEvent,
        icon = Icons.Filled.WarningAmber,
        title = stringResource(R.string.silent_panic),
        hint = stringResource(R.string.panic_hold_hint),
        pulsing = true,
        onClick = onTapHint,
        modifier = modifier,
        onLongClick = onPanic,
        longClickLabel = stringResource(R.string.panic_hold_action_label),
    )
}

/**
 * The periodic signal's own card (A-33): the time left on the running cycle, and a tap that restarts
 * it from now. It is the same card shape and height as Panic and the Motion control, so it reads as
 * one of the dashboard's actions rather than a status panel.
 *
 * The countdown is computed on the monotonic clock, from the deadline the scheduler stored, so moving
 * the system clock cannot make this card show more time than the cycle really has left. Nothing here
 * can start a cycle: the card only exists because a deadline exists, and a press only re-arms the one
 * the owner already switched on.
 */
@Composable
private fun PeriodicSignalAction(
    deadline: CountdownDeadline,
    intervalMs: Long,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) {
        nowElapsedMs = SystemClock.elapsedRealtime()
        while (true) {
            delay(1_000L)
            nowElapsedMs = SystemClock.elapsedRealtime()
        }
    }
    val day = stringResource(R.string.unit_day)
    val hour = stringResource(R.string.unit_hour)
    val minute = stringResource(R.string.unit_minute)
    val lessThanMinute = stringResource(R.string.unit_less_than_minute)
    PulseActionCard(
        accent = ShieldraTheme.semantic.protection,
        icon = Icons.Filled.Timer,
        title = stringResource(
            R.string.periodic_signal_card_title,
            RemainingTime.format(deadline.remainingMs(nowElapsedMs), day, hour, minute, lessThanMinute),
        ),
        hint = stringResource(
            R.string.periodic_signal_card_hint,
            RemainingTime.format(
                intervalMs.coerceAtLeast(CountdownDeadline.MIN_DURATION_MS),
                day, hour, minute, lessThanMinute,
            ),
        ),
        pulsing = true,
        onClick = onReset,
        modifier = modifier,
    )
}

/**
 * The owner's Start/Stop control for Motion monitoring. It asks for the transition and nothing more:
 * whether the card says Start or Stop is derived from the guard tile above, which is itself derived
 * from the state the service publishes, so a press can never make the screen claim monitoring. It
 * pulses only while monitoring is really on.
 */
@Composable
private fun MotionMonitoringAction(
    monitoring: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PulseActionCard(
        accent = ShieldraTheme.semantic.protection,
        icon = Icons.Filled.Sensors,
        title = stringResource(if (monitoring) R.string.motion_card_on_title else R.string.motion_card_off_title),
        hint = stringResource(if (monitoring) R.string.motion_card_on_hint else R.string.motion_card_off_hint),
        pulsing = monitoring,
        onClick = onToggle,
        modifier = modifier,
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

/**
 * The Pro button in the top bar (A-40): gold and gently breathing while the plan is Free, teal and calm
 * once Pro is active. Both open the Pro screen (buy, or manage).
 */
@Composable
private fun ProPill(active: Boolean, onClick: () -> Unit) {
    val color = if (active) ShieldraTheme.semantic.protection else ShieldraTheme.semantic.suspicious
    val transition = rememberInfiniteTransition(label = "pro_pill")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pro_pill_glow",
    )
    val borderAlpha = if (active) 0.5f else glow
    Row(
        modifier = Modifier
            .padding(end = ShieldraTheme.spacing.s)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.10f))))
            .border(1.dp, color.copy(alpha = borderAlpha), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.pro_pill),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
