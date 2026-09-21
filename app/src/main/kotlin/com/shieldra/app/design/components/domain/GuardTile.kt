package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.icons.icon
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.model.GuardKind
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
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                enabled = guard.enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(spacing.l)
            .semantics(mergeDescendants = true) {
                val premiumSuffix = if (guard.isPremiumLocked) ". Premium feature." else ""
                contentDescription =
                    "${guard.title}. ${guard.subtitle}. Status: ${guard.status.name}.$premiumSuffix"
            },
    ) {
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

@Preview(showBackground = true)
@Composable
private fun GuardTilePreview() {
    ShieldraTheme(darkTheme = true) {
        GuardTile(
            GuardUiModel(
                kind = GuardKind.Lock,
                title = "Lock Guard",
                subtitle = "Active · 2 attempts",
                status = GuardStatus.Active,
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp).width(180.dp),
        )
    }
}
