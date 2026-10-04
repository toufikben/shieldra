package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.EventId
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.shieldra.domain.DeliveryStatus as RecordedStatus

/** A-32: receipts and the History chip come only from attempts the delivery path recorded. */
class DeliveryReceiptProjectionTest {
    private val at = Instant.parse("2026-10-04T10:15:30Z")

    @Test
    fun an_event_with_no_recorded_attempt_has_no_receipt_and_keeps_its_state_label() {
        val event = event(emptyList())

        assertTrue(event.deliveryReceipts(ZoneOffset.UTC).isEmpty())
        assertNull(event.recordedDeliveryStatus())
    }

    @Test
    fun the_latest_attempt_per_channel_is_the_receipt_and_a_later_success_wins() {
        val event = event(
            listOf(
                DeliveryInformation("TELEGRAM", RecordedStatus.DEFERRED, at, "network: UnknownHostException"),
                DeliveryInformation("TELEGRAM", RecordedStatus.SUCCESS, at),
                DeliveryInformation("EMAIL", RecordedStatus.FAILED, at, "mail server answered 535"),
            ),
        )

        val receipts = event.deliveryReceipts(ZoneOffset.UTC)

        assertEquals(2, receipts.size)
        val telegram = receipts.single { it.channel == ChannelId.Telegram }
        assertEquals(DeliveryStatus.Delivered, telegram.status)
        assertEquals("10:15:30", telegram.detail)
        val email = receipts.single { it.channel == ChannelId.Email }
        assertEquals(DeliveryStatus.Failed, email.status)
        assertEquals("mail server answered 535", email.detail)
        assertEquals(DeliveryStatus.Delivered, event.recordedDeliveryStatus())
    }

    @Test
    fun only_deferred_attempts_read_as_retrying() {
        val event = event(listOf(DeliveryInformation("EMAIL", RecordedStatus.DEFERRED, at, "offline")))

        assertEquals(DeliveryStatus.Deferred, event.recordedDeliveryStatus())
    }

    private fun event(attempts: List<DeliveryInformation>): SecurityEvent = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = at,
        deliveryInformation = attempts,
        type = SecurityEventType.PANIC_ACTIVATION,
    )
}
