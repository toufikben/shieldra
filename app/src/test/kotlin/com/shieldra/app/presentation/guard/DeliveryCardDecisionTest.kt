package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.ChannelReceiptUiModel
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F-K13: Event Detail added the Delivery card whatever its receipts were, so the owner's phone read a
 * "Delivery" heading over an empty body for an event no channel was ever offered to. A section header
 * is itself a claim, so the decision to render it lives outside Compose and is pinned here. Nothing in
 * this file manufactures a receipt: the card can only become visible from one that already exists.
 */
class DeliveryCardDecisionTest {
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @Test
    fun fk13_an_event_with_no_delivery_receipts_shows_no_delivery_card() {
        assertFalse(
            deliveryCardVisible(emptyList()),
            "A heading over an empty body claims a delivery section that has no deliveries",
        )
    }

    @Test
    fun fk13_a_recorded_receipt_still_reaches_the_delivery_card() {
        assertTrue(
            deliveryCardVisible(listOf(receipt(DeliveryStatus.Delivered))),
            "Hiding an empty section must not hide a real receipt with it",
        )
    }

    @Test
    fun fk13_the_persisted_read_path_produces_a_detail_with_no_delivery_card() {
        val persisted = SecurityEvent.create(
            eventId = EventId.from(UUID.randomUUID()),
            createdAt = now.minus(Duration.ofMinutes(1)),
            state = EventState.INITIATED,
            type = SecurityEventType.PANIC_ACTIVATION,
        )
        val reader = PersistedEventReader(storageInstalled = true, loadRecent = { listOf(persisted) })

        val detail = assertNotNull(reader.readOne(persisted.id.value.toString(), now = now))

        assertFalse(
            deliveryCardVisible(detail.delivery),
            "The real read path records no delivery attempt, so the detail screen must print no " +
                "delivery section for it",
        )
    }

    private fun receipt(status: DeliveryStatus): ChannelReceiptUiModel =
        ChannelReceiptUiModel(ChannelId.Email, status, "14:32")
}
