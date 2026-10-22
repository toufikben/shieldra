package com.shieldra.app.design.components.domain

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.icons.icon
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.presentation.demo.DemoData
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.app.presentation.model.GuardUiModel

@Composable
private fun statusColor(status: GuardStatus): Color = when (status) {
    GuardStatus.Active -> ShieldraTheme.semantic.protection
    GuardStatus.Signal -> ShieldraTheme.semantic.suspicious
    GuardStatus.Disabled -> ShieldraTheme.semantic.disabledState
    GuardStatus.Locked -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun GuardTile(
    guard: GuardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val color = statusColor(guard.status)
    val shape = ShieldraCardShape
    val statusLabel = stringResource(when (guard.status) {
        GuardStatus.Active -> R.string.guard_status_active
        GuardStatus.Signal -> R.string.guard_status_signal
        GuardStatus.Disabled -> R.string.guard_status_disabled
        GuardStatus.Locked -> R.string.guard_status_locked
    })
    val premiumSuffix = if (guard.isPremiumLocked) {
        " ${stringResource(R.string.guard_premium_feature)}."
    } else {
        ""
    }
    val accessibilityDescription = stringResource(
        R.string.guard_accessibility,
        guard.title,
        guard.subtitle,
        statusLabel,
        premiumSuffix,
    )

    val active = guard.status == GuardStatus.Active
    // Active guards get a premium tinted-glass fill with a hairline accent border; the rest keep the
    // plain surface. The status still comes from the model, so the look never overstates protection.
    val containerModifier = if (active) {
        Modifier
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.04f)),
                ),
            )
            .border(1.dp, color.copy(alpha = 0.30f), shape)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surface)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .then(containerModifier)
            .clickable(
                enabled = guard.enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(spacing.l)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = guard.kind.icon(),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (active) LiveDot(color)
        }

        Spacer(Modifier.height(spacing.m))

        Text(
            text = guard.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (guard.isPremiumLocked) {
                Text(
                    text = "★ ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = guard.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(spacing.m))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (guard.status == GuardStatus.Disabled)
                        color.copy(alpha = 0.3f)
                    else color,
                ),
        )
    }
}

/** A small breathing dot that signals a guard is live. */
@Composable
private fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "guard_live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "guard_live_alpha",
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

@Preview(showBackground = true)
@Composable
private fun GuardTilePreview() {
    ShieldraTheme(darkTheme = true) {
        GuardTile(
            guard = DemoData.guards().first(),
            onClick = {},
            modifier = Modifier.padding(16.dp).width(180.dp),
        )
    }
}
