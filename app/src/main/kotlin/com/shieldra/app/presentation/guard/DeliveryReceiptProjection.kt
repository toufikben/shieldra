package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.ChannelReceiptUiModel
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.SecurityEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.shieldra.domain.DeliveryStatus as RecordedStatus

/**
 * Turns the delivery attempts an event really has recorded into receipts (F-K13, A-32). This is the
 * only place a receipt is built, and only from `deliveryInformation`: an event no channel was ever
 * offered to has no attempt, so it gets no receipt and no Delivery card.
 *
 * One receipt per channel: the latest attempt on that channel is the one that tells the owner where
 * the alert stands (a DEFERRED attempt later followed by SUCCESS reads as delivered).
 */
internal fun SecurityEvent.deliveryReceipts(zone: ZoneId = ZoneId.systemDefault()): List<ChannelReceiptUiModel> =
    deliveryInformation
        .groupBy { it.channel }
        .mapNotNull { (_, attempts) -> attempts.last().toReceipt(zone) }

/** What the History chip may say once real attempts exist; null leaves the state-based label. */
internal fun SecurityEvent.recordedDeliveryStatus(): DeliveryStatus? {
    val latest = deliveryInformation.groupBy { it.channel }.map { (_, attempts) -> attempts.last().status }
    return when {
        latest.isEmpty() -> null
        RecordedStatus.SUCCESS in latest -> DeliveryStatus.Delivered
        RecordedStatus.DEFERRED in latest -> DeliveryStatus.Deferred
        RecordedStatus.FAILED in latest -> DeliveryStatus.Failed
        else -> null
    }
}

private fun DeliveryInformation.toReceipt(zone: ZoneId): ChannelReceiptUiModel? {
    val channelId = when (channel) {
        "EMAIL" -> ChannelId.Email
        "TELEGRAM" -> ChannelId.Telegram
        "WHATSAPP" -> ChannelId.WhatsApp
        else -> return null
    }
    val status = when (status) {
        RecordedStatus.SUCCESS -> DeliveryStatus.Delivered
        RecordedStatus.DEFERRED -> DeliveryStatus.Deferred
        RecordedStatus.FAILED -> DeliveryStatus.Failed
        RecordedStatus.SKIPPED -> return null
    }
    val time = occurredAt?.let { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone).format(it) }
    return ChannelReceiptUiModel(
        channel = channelId,
        status = status,
        detail = if (status == DeliveryStatus.Delivered) time else failureReason ?: time,
    )
}
