package com.shieldra.delivery

import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Alerts that could not leave the phone yet, kept until a network comes back. */
interface PendingAlertStore {
    fun add(alert: SecurityAlert)

    fun drain(): List<SecurityAlert>
}

/**
 * Sends an alert through every channel the owner switched on, off the caller's thread, and records
 * on the event exactly what happened per channel (A-32): `SUCCESS` only when the other end accepted
 * it, `DEFERRED` when it could not go out yet and is kept for later, `FAILED` when retrying cannot
 * help. Nothing is announced to whoever holds the phone: no notification, no sound, no screen.
 */
class AlertDispatcher(
    private val settings: () -> ChannelSource,
    private val repository: () -> EventRepository?,
    private val wording: () -> AlertWording,
    private val pending: PendingAlertStore,
    private val now: () -> Instant = { Instant.now() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val zone: ZoneId = ZoneId.systemDefault(),
    /**
     * Opens the alert's evidence (the encrypted front-camera photo) and carries its location, just
     * before sending. It is resolved per delivery, never stored on the queued alert, so a decrypted
     * photo never sits in preferences or in the pending store.
     */
    private val extras: (SecurityAlert) -> AlertExtras = { AlertExtras.NONE },
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "shieldra-alerts").apply { isDaemon = true }
    },
) {
    /** Queues [alert] and returns at once; the caller may be the sensor thread or the UI. */
    fun dispatch(alert: SecurityAlert) {
        worker.execute { deliver(alert) }
    }

    /** Retries everything kept while offline. Called when a network becomes available. */
    fun flushPending() {
        worker.execute { pending.drain().forEach(::deliver) }
    }

    /** Sends through one channel now and reports the answer; used by the "send test" button. */
    fun sendNow(channel: AlertChannel, alert: SecurityAlert): ChannelResult {
        val text = wording()
        val carried = extrasFor(alert)
        return channel.send(
            AlertText.subject(alert, text),
            AlertText.body(alert, text, zone, photoAttached = carried.photoJpeg != null),
            carried,
        )
    }

    private fun deliver(alert: SecurityAlert) {
        val channels = settings().activeChannels()
        if (channels.isEmpty()) return
        val text = wording()
        val carried = extrasFor(alert)
        val subject = AlertText.subject(alert, text)
        val body = AlertText.body(alert, text, zone, photoAttached = carried.photoJpeg != null)
        var keepForLater = false
        channels.forEach { channel ->
            val result = sendWithRetry(channel, subject, body, carried)
            if (result is ChannelResult.Retryable) keepForLater = true
            record(alert, channel.channel, result)
        }
        if (keepForLater) pending.add(alert)
    }

    private fun extrasFor(alert: SecurityAlert): AlertExtras =
        runCatching { extras(alert) }.getOrDefault(AlertExtras.NONE)

    private fun sendWithRetry(channel: AlertChannel, subject: String, body: String, carried: AlertExtras): ChannelResult {
        var result: ChannelResult = ChannelResult.Retryable("not attempted")
        for ((attempt, waitMs) in RETRY_WAITS_MS.withIndex()) {
            if (attempt > 0) sleep(waitMs)
            result = runCatching { channel.send(subject, body, carried) }
                .getOrElse { ChannelResult.Retryable("unexpected: ${it.javaClass.simpleName}") }
            if (result !is ChannelResult.Retryable) return result
        }
        return result
    }

    private fun record(alert: SecurityAlert, channel: DeliveryChannel, result: ChannelResult) {
        val eventId = alert.eventId?.let { runCatching { EventId.from(UUID.fromString(it)) }.getOrNull() } ?: return
        val information = when (result) {
            ChannelResult.Sent -> DeliveryInformation(channel.name, DeliveryStatus.SUCCESS, now())
            is ChannelResult.Retryable -> DeliveryInformation(channel.name, DeliveryStatus.DEFERRED, now(), result.reason)
            is ChannelResult.Rejected -> DeliveryInformation(channel.name, DeliveryStatus.FAILED, now(), result.reason)
        }
        runCatching { repository()?.recordDeliveryAttempt(eventId, information) }
    }

    private companion object {
        /** Three tries per channel: at once, after 2 s, after 8 s. Longer gaps belong to the pending queue. */
        val RETRY_WAITS_MS = listOf(0L, 2_000L, 8_000L)
    }
}
