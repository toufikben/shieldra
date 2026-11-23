package com.shieldra.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P1-3: a queued alert must survive a failed flush. Draining the store up front loses every alert in
 * the batch when the process dies or the network drops mid-send, and orders them by their encoded text
 * rather than by when they happened.
 */
class PendingAlertQueueTest {
    private val first = alertAt("2026-11-23T10:00:00Z")
    private val second = alertAt("2026-11-23T10:05:00Z")
    private val third = alertAt("2026-11-23T10:10:00Z")

    @Test
    fun a_failure_during_flush_keeps_the_alerts_that_were_not_sent() {
        val queue = InMemoryPendingQueue(listOf(third, first, second))
        val sent = mutableListOf<SecurityAlert>()

        val started = queue.beginFlush()
        assertEquals(listOf(first, second, third), started.map { it.alert }, "a flush is oldest first")

        queue.markSent(started[0].key)
        queue.markSent(started[1].key)
        // The third send never happens: the process dies, or the network goes away with the worker.

        assertEquals(listOf(third), queue.pending(), "only the failed remainder may be retried")
    }

    @Test
    fun a_new_alert_is_never_truncated_by_an_unfinished_flush() {
        val queue = InMemoryPendingQueue(listOf(first))

        val started = queue.beginFlush()
        queue.add(second)

        assertEquals(listOf(second), queue.pending(), "an in-flight alert is not pending, the new one is")
        assertEquals(listOf(first), started.map { it.alert })
    }

    @Test
    fun a_completed_flush_leaves_nothing_behind() {
        val queue = InMemoryPendingQueue(listOf(first, second))

        queue.beginFlush().forEach { queue.markSent(it.key) }

        assertTrue(queue.pending().isEmpty())
        assertTrue(queue.beginFlush().isEmpty(), "an empty queue must not invent a send")
    }

    private fun alertAt(instant: String) = SecurityAlert(
        eventId = "00000000-0000-0000-0000-000000000001",
        kind = AlertKind.MOTION,
        occurredAt = Instant.parse(instant),
        device = "realme RMX3910",
        batteryPercent = 54,
    )
}
