package com.shieldra.delivery

import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.InMemoryEventRepository
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A-32: the dispatcher records exactly what each channel really did, and keeps what could not go. */
class AlertDispatcherTest {
    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private val repository = InMemoryEventRepository()
    private val event = SecurityEvent.create(
        eventId = EventId.from(UUID.randomUUID()),
        createdAt = now,
        severity = GuardSeverity.CRITICAL,
        type = SecurityEventType.PANIC_ACTIVATION,
    ).also { repository.save(it) }
    private val pending = MemoryPending()

    @Test
    fun a_sent_alert_is_recorded_as_success_on_its_event() {
        val telegram = FakeChannel(DeliveryChannel.TELEGRAM, ChannelResult.Sent)

        dispatcher(listOf(telegram)).dispatch(alert())

        val attempts = repository.find(event.id)!!.deliveryInformation
        assertEquals(listOf("TELEGRAM" to DeliveryStatus.SUCCESS), attempts.map { it.channel to it.status })
        assertEquals(1, telegram.calls)
        assertTrue(pending.items.isEmpty())
    }

    @Test
    fun a_retryable_failure_is_tried_three_times_recorded_as_deferred_and_kept_for_later() {
        val email = FakeChannel(DeliveryChannel.EMAIL, ChannelResult.Retryable("offline"))

        dispatcher(listOf(email)).dispatch(alert())

        assertEquals(3, email.calls)
        assertEquals(DeliveryStatus.DEFERRED, repository.find(event.id)!!.deliveryInformation.single().status)
        assertEquals(listOf(alert()), pending.items)
    }

    @Test
    fun a_refused_channel_is_recorded_as_failed_once_and_not_kept() {
        val email = FakeChannel(DeliveryChannel.EMAIL, ChannelResult.Rejected("bad password"))
        val telegram = FakeChannel(DeliveryChannel.TELEGRAM, ChannelResult.Sent)

        dispatcher(listOf(email, telegram)).dispatch(alert())

        assertEquals(1, email.calls)
        val attempts = repository.find(event.id)!!.deliveryInformation.associate { it.channel to it.status }
        assertEquals(mapOf("EMAIL" to DeliveryStatus.FAILED, "TELEGRAM" to DeliveryStatus.SUCCESS), attempts)
        assertTrue(pending.items.isEmpty())
    }

    @Test
    fun the_message_carries_the_real_facts_and_a_test_alert_records_nothing() {
        val telegram = FakeChannel(DeliveryChannel.TELEGRAM, ChannelResult.Sent)

        dispatcher(listOf(telegram)).dispatch(alert().copy(eventId = null, kind = AlertKind.TEST))

        assertTrue(repository.find(event.id)!!.deliveryInformation.isEmpty())
        val body = telegram.lastBody
        assertTrue("Test alert" in body)
        assertTrue("RMX3910" in body)
        assertTrue("54%" in body)
        assertTrue("2026-10-04 10:00:00" in body)
    }

    @Test
    fun nothing_is_sent_or_recorded_when_no_channel_is_switched_on() {
        dispatcher(emptyList()).dispatch(alert())

        assertTrue(repository.find(event.id)!!.deliveryInformation.isEmpty())
        assertTrue(pending.items.isEmpty())
    }

    private fun alert() = SecurityAlert(
        eventId = event.id.value.toString(),
        kind = AlertKind.PANIC,
        occurredAt = now,
        device = "realme RMX3910",
        batteryPercent = 54,
    )

    private fun dispatcher(channels: List<AlertChannel>) = AlertDispatcher(
        settings = { FixedSettings(channels) },
        repository = { repository },
        wording = {
            AlertWording(
                appName = "SHIELDRA",
                panicTitle = "Silent panic pressed",
                motionTitle = "Movement detected",
                testTitle = "Test alert",
                timeLabel = "Time",
                deviceLabel = "Phone",
                batteryLabel = "Battery",
                eventLabel = "Event",
                locale = Locale.ROOT,
            )
        },
        pending = pending,
        now = { now },
        sleep = {},
        zone = ZoneOffset.UTC,
        worker = DirectExecutor(),
    )

    /** Settings whose active channels are the fakes under test. */
    private class FixedSettings(private val channels: List<AlertChannel>) : ChannelSource {
        override fun activeChannels(): List<AlertChannel> = channels
    }

    private class FakeChannel(override val channel: DeliveryChannel, private val result: ChannelResult) : AlertChannel {
        var calls = 0
        var lastBody = ""

        override fun send(subject: String, body: String): ChannelResult {
            calls++
            lastBody = body
            return result
        }
    }

    private class MemoryPending : PendingAlertStore {
        val items = mutableListOf<SecurityAlert>()

        override fun add(alert: SecurityAlert) {
            items += alert
        }

        override fun drain(): List<SecurityAlert> = items.toList().also { items.clear() }
    }

    /** Runs submitted work on the calling thread, so assertions see its effects immediately. */
    private class DirectExecutor : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
