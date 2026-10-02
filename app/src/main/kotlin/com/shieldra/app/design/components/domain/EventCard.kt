package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
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
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.design.tokens.ShieldraPillShape
import com.shieldra.app.presentation.demo.DemoData
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.guard.deliveryChipLabelRes
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.EventUiModel

@Composable
private fun deliveryColor(status: DeliveryStatus): Color = when (status) {
    DeliveryStatus.Delivered -> ShieldraTheme.semantic.protection
    DeliveryStatus.Delivering -> ShieldraTheme.semantic.suspicious
    DeliveryStatus.Deferred -> ShieldraTheme.semantic.suspicious
    DeliveryStatus.Ready -> MaterialTheme.colorScheme.onSurfaceVariant
    DeliveryStatus.Failed -> MaterialTheme.colorScheme.error
    DeliveryStatus.Expired -> MaterialTheme.colorScheme.onSurfaceVariant
    DeliveryStatus.NotAttempted -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The chip's sentence comes from the pure decision beside the model, so a recorded
 * event that no delivery ever claimed is worded as "not attempted" instead of the
 * "Skipped" this build could not prove (Q-K-4, F-K10).
 */
@Composable
private fun deliveryLabel(status: DeliveryStatus): String = stringResource(deliveryChipLabelRes(status))

@Composable
fun EventCard(
    event: EventUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val shape = ShieldraCardShape
    val deliveryDescription = stringResource(
        R.string.delivery_label,
        deliveryLabel(event.deliveryStatus),
    )
    val accessibilityDescription = stringResource(
        R.string.event_accessibility,
        event.title,
        event.timestampLabel,
        deliveryDescription,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.l)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = event.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(spacing.m))

        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = event.timestampLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.s),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.xs),
            ) {
                if (event.hasPhoto) Pill(stringResource(R.string.event_photo), MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.hasLocation) Pill(stringResource(R.string.event_location), MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant)
                val dColor = deliveryColor(event.deliveryStatus)
                Pill(deliveryLabel(event.deliveryStatus), dColor.copy(alpha = 0.12f), dColor)
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .clip(ShieldraPillShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EventCardPreview() {
    ShieldraTheme(darkTheme = true) {
        EventCard(
            event = DemoData.recentEvents().first(),
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
