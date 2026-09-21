package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.shieldra.app.design.tokens.ShieldraPillShape
import com.shieldra.app.presentation.model.DeliveryStatus
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
    DeliveryStatus.Skipped -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun deliveryLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.Ready -> "Ready"
    DeliveryStatus.Delivering -> "Sending"
    DeliveryStatus.Delivered -> "Delivered"
    DeliveryStatus.Deferred -> "Retrying"
    DeliveryStatus.Failed -> "Failed"
    DeliveryStatus.Expired -> "Expired"
    DeliveryStatus.Skipped -> "Skipped"
}

@Composable
fun EventCard(
    event: EventUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.l)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${event.title}. ${event.timestampLabel}. " +
                    "Delivery: ${deliveryLabel(event.deliveryStatus)}."
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                if (event.hasPhoto) Pill("Photo", MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.hasLocation) Pill("Location", MaterialTheme.colorScheme.surfaceVariant,
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
            event = EventUiModel(
                id = "evt_1",
                type = EventType.FailedUnlock,
                title = "Failed unlock attempt",
                subtitle = "Attempt 2",
                timestampLabel = "Today · 14:32",
                deliveryStatus = DeliveryStatus.Delivered,
                hasPhoto = true,
                hasLocation = true,
            ),
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
