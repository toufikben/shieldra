package com.shieldra.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1-3: the queue behind the pending-alert store must order by when an alert happened and retire
 * exactly what was sent. An emptied queue loses the rest of the batch when one send fails.
 */
class PendingAlertQueueTest {
    @Test
    fun the_queue_is_ordered_by_when_the_alert_happened() {
        val queue = PendingAlertQueue(capacity = 50)
        queue.add("third", occurredAtMs = 300L)
        queue.add("first", occurredAtMs = 100L)
        queue.add("second", occurredAtMs = 200L)

        assertEquals(listOf("first", "second", "third"), queue.pending().map { it.key })
    }

    @Test
    fun retiring_one_alert_leaves_every_other_alert_queued() {
        val queue = PendingAlertQueue(capacity = 50)
        queue.add("first", occurredAtMs = 100L)
        queue.add("second", occurredAtMs = 200L)

        queue.markSent("first")

        assertEquals(listOf("second"), queue.pending().map { it.key })
    }

    @Test
    fun a_full_queue_drops_the_oldest_alert_not_the_first_alphabetically() {
        val queue = PendingAlertQueue(capacity = 2)
        queue.add("z-oldest", occurredAtMs = 100L)
        queue.add("a-newest", occurredAtMs = 300L)
        queue.add("m-middle", occurredAtMs = 200L)

        assertEquals(listOf("m-middle", "a-newest"), queue.pending().map { it.key })
    }

    @Test
    fun an_empty_queue_reports_nothing_to_send() {
        assertTrue(PendingAlertQueue(capacity = 50).pending().isEmpty())
    }
}
