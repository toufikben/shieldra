package com.shieldra.delivery

import android.util.Log
import com.shieldra.domain.DeliveryInformation
import com.shieldra.domain.DeliveryStatus
import com.shieldra.domain.EventId
import com.shieldra.domain.EventLifecycle
import com.shieldra.domain.EventRepository
import com.shieldra.domain.RepositoryResult
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
    private val logError: (String, Throwable?) -> Unit = { message, error -> Log.e(TAG, message, error) },
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
        val eventId = eventIdFor(alert)
        val lifecycle = eventId?.let { id -> repository()?.let { EventLifecycle(it) } }
        val deliveryStarted = if (eventId != null && lifecycle != null) {
            runCatching { lifecycle.beginDelivery(eventId) }
                .onFailure { logError("delivery state could not be persisted for $eventId", it) }
                .getOrDefault(false)
        } else {
            eventId == null
        }
        if (!deliveryStarted) {
            logError("delivery state was refused for $eventId; the alert stays retryable", null)
        }
        val source = settings()
        val channels = source.activeChannels()
        val unusable = source.unusableChannels()
        if (channels.isEmpty() && unusable.isEmpty()) {
            // With no channel to carry it, an alert whose delivery state could not be persisted is
            // kept rather than dropped; an owner who has configured nothing yet keeps the documented
            // FAILED_FINAL receipt instead of an unbounded queue.
            completeDelivery(alert, eventId, lifecycle, deferred = !deliveryStarted)
            return
        }
        val text = wording()
        val carried = extrasFor(alert)
        val subject = AlertText.subject(alert, text)
        val body = AlertText.body(alert, text, zone, photoAttached = carried.photoJpeg != null)
        var keepForLater = false
        var receiptPersistenceFailed = false
        channels.forEach { channel ->
            val result = sendWithRetry(channel, subject, body, carried)
            if (result is ChannelResult.Retryable) keepForLater = true
            if (!record(alert, channel.channel, result)) receiptPersistenceFailed = true
        }
        // Audit development (ز) / B-4: a channel the owner switched on that cannot be opened is a
        // delivery failure, and a delivery failure is a security fact — the event records FAILED so
        // the guard card shows a receipt instead of nothing. The alert is kept, so re-entering the
        // secret sends it once the channel works again.
        unusable.forEach {
            if (!record(alert, it.channel, ChannelResult.Rejected(it.reason))) receiptPersistenceFailed = true
        }
        // A remote success without its durable SUCCESS receipt is not DELIVERED. Keep the alert for
        // an at-least-once retry instead of laundering a storage failure into a completed incident.
        val deferred = keepForLater || unusable.isNotEmpty() || receiptPersistenceFailed || !deliveryStarted
        completeDelivery(alert, eventId, lifecycle, deferred)
    }

    /**
     * Queues the alert exactly once when the incident is not durably completed. Requeueing from both
     * the deferred branch and the refused-completion branch would leave two copies for one incident.
     */
    private fun completeDelivery(
        alert: SecurityAlert,
        eventId: EventId?,
        lifecycle: EventLifecycle?,
        deferred: Boolean,
    ) {
        if (eventId == null || lifecycle == null) {
            if (deferred) keepForRetry(alert, eventId)
            return
        }
        val finished = runCatching { lifecycle.finishDelivery(eventId, deferred) }
            .onFailure { logError("delivery completion could not be persisted for $eventId", it) }
            .getOrDefault(false)
        if (finished && !deferred) return
        // A deferred incident still owes a send, and a refused completion leaves the event reading
        // DELIVERING with nothing else to retry it. Either way the alert is queued exactly once.
        if (!finished) {
            logError("delivery completion was refused for $eventId; the alert is kept for a retry", null)
        }
        keepForRetry(alert, eventId)
    }

    /** Keeps one copy per alert in the retry store; a duplicate copy would resend the same event. */
    private fun keepForRetry(alert: SecurityAlert, eventId: EventId?) {
        runCatching { pending.add(alert) }
            .onFailure { logError("deferred alert could not be queued for $eventId", it) }
    }

    private fun eventIdFor(alert: SecurityAlert): EventId? =
        alert.eventId?.let { runCatching { EventId.from(UUID.fromString(it)) }.getOrNull() }

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

    private fun record(alert: SecurityAlert, channel: DeliveryChannel, result: ChannelResult): Boolean {
        val eventId = eventIdFor(alert) ?: return true
        val information = when (result) {
            ChannelResult.Sent -> DeliveryInformation(channel.name, DeliveryStatus.SUCCESS, now())
            is ChannelResult.Retryable -> DeliveryInformation(channel.name, DeliveryStatus.DEFERRED, now(), result.reason)
            is ChannelResult.Rejected -> DeliveryInformation(channel.name, DeliveryStatus.FAILED, now(), result.reason)
        }
        val store = repository() ?: return false
        return runCatching {
            when (store.recordDeliveryAttempt(eventId, information)) {
                RepositoryResult.Applied -> true
                RepositoryResult.AlreadyExists ->
                    store.find(eventId)?.deliveryInformation?.contains(information) == true
                else -> false
            }
        }.getOrElse {
            logError("delivery receipt could not be stored for $eventId", it)
            false
        }.also { persisted ->
            if (!persisted) logError("delivery receipt was refused for $eventId", null)
        }
    }

    private companion object {
        const val TAG = "AlertDispatcher"

        /** Three tries per channel: at once, after 2 s, after 8 s. Longer gaps belong to the pending queue. */
        val RETRY_WAITS_MS = listOf(0L, 2_000L, 8_000L)
    }
}
