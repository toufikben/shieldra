package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.ChannelReceiptUiModel
import com.shieldra.app.presentation.model.DeliveryStatus

@Composable
private fun statusColor(status: DeliveryStatus): Color = when (status) {
    DeliveryStatus.Delivered -> ShieldraTheme.semantic.protection
    DeliveryStatus.Failed -> MaterialTheme.colorScheme.error
    DeliveryStatus.Delivering -> ShieldraTheme.semantic.suspicious
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.Delivered -> stringResource(R.string.delivery_delivered)
    DeliveryStatus.Failed -> stringResource(R.string.delivery_failed)
    DeliveryStatus.Skipped -> stringResource(R.string.delivery_skipped)
    DeliveryStatus.Delivering -> stringResource(R.string.delivery_sending)
    DeliveryStatus.Ready -> stringResource(R.string.delivery_ready)
    DeliveryStatus.Deferred -> stringResource(R.string.delivery_retrying)
    DeliveryStatus.Expired -> stringResource(R.string.delivery_expired)
}

@Composable
private fun channelLabel(channel: ChannelId): String = stringResource(when (channel) {
    ChannelId.Email -> R.string.channel_email
    ChannelId.WhatsApp -> R.string.channel_whatsapp
    ChannelId.Telegram -> R.string.channel_telegram
})

@Composable
fun DeliveryReceipt(
    receipts: List<ChannelReceiptUiModel>,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.l),
    ) {
        Text(
            text = stringResource(R.string.delivery_section),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.s))
        receipts.forEachIndexed { index, receipt ->
            ChannelRow(receipt)
            if (index < receipts.lastIndex) Spacer(Modifier.height(spacing.s))
        }
    }
}

@Composable
private fun ChannelRow(receipt: ChannelReceiptUiModel) {
    val spacing = ShieldraTheme.spacing
    val color = statusColor(receipt.status)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channelLabel(receipt.channel).take(1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = channelLabel(receipt.channel),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (receipt.detail != null) {
                Text(
                    text = receipt.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (receipt.status == DeliveryStatus.Failed)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = statusLabel(receipt.status),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeliveryReceiptPreview() {
    ShieldraTheme(darkTheme = true) {
        DeliveryReceipt(
            receipts = listOf(
                ChannelReceiptUiModel(ChannelId.Email, DeliveryStatus.Delivered, "14:32"),
                ChannelReceiptUiModel(ChannelId.WhatsApp, DeliveryStatus.Failed, "Invalid recipient"),
                ChannelReceiptUiModel(ChannelId.Telegram, DeliveryStatus.Delivered, "14:32"),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
