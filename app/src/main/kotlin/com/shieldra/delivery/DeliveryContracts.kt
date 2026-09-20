package com.shieldra.delivery

import java.time.Instant

enum class DeliveryChannel {
    EMAIL,
    WHATSAPP,
    TELEGRAM,
}

enum class DeliveryOutcome {
    SUCCESS,
    FAILED,
    DEFERRED,
    SKIPPED,
}

data class DeliveryReceipt(
    val channel: DeliveryChannel,
    val outcome: DeliveryOutcome,
    val requestedAt: Instant,
    val completedAt: Instant? = null,
    val failureReason: String? = null,
)

interface DeliveryOrchestrator

interface ChannelAdapter {
    val channel: DeliveryChannel
}

interface SmartQueue
