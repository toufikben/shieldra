package com.shieldra.protection

import com.shieldra.domain.SecurityEventType

/**
 * A capture the guards asked for but could not run (A-33 capture-path fix).
 *
 * It is a *request to photograph now*, never a photograph: nothing here holds media, a position or a
 * secret. [requestedAtEpochMs] is the part that matters most, because a capture that finally runs
 * long after the moment it was asked for is a picture of whoever holds the phone now — which may be
 * the owner — so an old request is dropped rather than delayed (see [PendingCaptureLogic.isFresh]).
 *
 * [eventId] is set when the request belongs to an incident that already exists (the Motion guard
 * creates its event before asking for the photo); null means the capture must find or open the
 * incident for [type], exactly as a live request would.
 *
 * [announced] records that the owner has already been told in text that this request produced no
 * photo. A request that keeps failing is retried for a bounded window, and without this mark each
 * retry would send the same bad news again — a stolen phone would then message the owner every
 * thirty seconds instead of once.
 */
data class PendingCapture(
    val type: SecurityEventType,
    val eventId: String?,
    val failedAttempts: Int?,
    val requestedAtEpochMs: Long,
    val announced: Boolean = false,
)

/**
 * What one look at the queue found: [due] may still run, [expired] waited past the moment it asked
 * for. Nothing is left in the middle, because a request that will never produce a photo still owes
 * the owner the news in text.
 */
data class Drain(
    val due: List<PendingCapture>,
    val expired: List<PendingCapture>,
) {
    val isEmpty: Boolean get() = due.isEmpty() && expired.isEmpty()
}

/**
 * The rules behind the queue of unrun captures, kept free of Android so they can be tested on the
 * JVM: how a request is written down and read back, how repeats collapse, how many may wait, and
 * when one stops being worth running.
 */
object PendingCaptureLogic {
    /**
     * How many requests may wait. Each one is a camera opening that takes seconds, so a long queue
     * would be stale before it drained; the newest face is the one worth catching, so the oldest
     * leaves first when the queue is full.
     */
    const val MAX_PENDING = 8

    /**
     * A request older than this is dropped instead of run. A live service drains the queue every
     * heartbeat (a minute) and again the moment it starts, so anything past this window did not fail
     * once — it kept failing, and the scene it was asked for is gone.
     */
    const val STALE_AFTER_MS = 5 * 60_000L

    /** The one-character delimiter the stored safe zones already use, so a field can never look like a break. */
    private const val SEPARATOR = "\u0001"

    /**
     * One line per request: guard, the event it belongs to if any, the attempt count, the moment, and
     * whether the owner has already been told in text.
     */
    fun encode(capture: PendingCapture): String = listOf(
        capture.type.name,
        capture.eventId.orEmpty(),
        capture.failedAttempts?.toString().orEmpty(),
        capture.requestedAtEpochMs.toString(),
        if (capture.announced) ANNOUNCED else "",
    ).joinToString(SEPARATOR)

    /**
     * A line written by another version, or damaged, decodes to null and is dropped, never guessed at.
     * A four-field line is a request written before the notice mark existed: it is read as not yet
     * announced, which is the honest reading — nobody had told the owner about it.
     */
    fun decode(raw: String): PendingCapture? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 4 && parts.size != 5) return null
        val type = runCatching { SecurityEventType.valueOf(parts[0]) }.getOrNull() ?: return null
        val requestedAt = parts[3].toLongOrNull() ?: return null
        return PendingCapture(
            type = type,
            eventId = parts[1].ifBlank { null },
            failedAttempts = parts[2].ifBlank { null }?.toLongOrNull()?.toInt(),
            requestedAtEpochMs = requestedAt,
            announced = parts.getOrNull(4) == ANNOUNCED,
        )
    }

    /**
     * Queues [capture] behind [existing]. Repeats collapse: one guard already waiting for the camera
     * is that guard's whole story, because the burst of wrong unlocks is one scene and the alert
     * groups it into one incident anyway. Anything already too old to run leaves on the way in, and
     * the oldest remaining request gives its place up when the queue is full.
     *
     * Returns the queue to store; it is the caller's job to notice that its own request is not in it.
     */
    fun plus(
        existing: List<PendingCapture>,
        capture: PendingCapture,
        limit: Int = MAX_PENDING,
    ): List<PendingCapture> {
        val waiting = existing.filter { isFresh(it, capture.requestedAtEpochMs) }
        if (waiting.any { it.type == capture.type && it.eventId == capture.eventId }) return waiting
        return (waiting + capture).takeLast(limit.coerceAtLeast(1))
    }

    /**
     * True while the request still describes a scene worth photographing. A negative age is refused
     * too: it means the wall clock went backwards or the phone rebooted since the request was made,
     * and in both cases the moment it asked for is already gone.
     */
    fun isFresh(
        capture: PendingCapture,
        nowEpochMs: Long,
        staleAfterMs: Long = STALE_AFTER_MS,
    ): Boolean = nowEpochMs - capture.requestedAtEpochMs in 0..staleAfterMs

    /**
     * The requests that may run now, oldest first, so the earliest face goes out first. Everything
     * past [STALE_AFTER_MS] is left out for good rather than kept waiting.
     */
    fun freshOnes(
        pending: List<PendingCapture>,
        nowEpochMs: Long,
        staleAfterMs: Long = STALE_AFTER_MS,
    ): List<PendingCapture> =
        pending.filter { isFresh(it, nowEpochMs, staleAfterMs) }.sortedBy { it.requestedAtEpochMs }

    /**
     * What one drain of the queue found: the requests that may still run, and the ones that waited
     * past their moment. Both lists are accounted for, because a guard request that stops being
     * runnable still has to reach the owner as text — the point of the queue is that no request
     * simply disappears.
     */
    fun split(
        pending: List<PendingCapture>,
        nowEpochMs: Long,
        staleAfterMs: Long = STALE_AFTER_MS,
    ): Drain {
        val due = freshOnes(pending, nowEpochMs, staleAfterMs)
        return Drain(
            due = due,
            expired = (pending.toSet() - due.toSet()).sortedBy { it.requestedAtEpochMs },
        )
    }

    /** Whether [pending] already holds a fresh request for this exact guard and incident. */
    fun holds(
        pending: List<PendingCapture>,
        type: SecurityEventType,
        eventId: String?,
        nowEpochMs: Long,
    ): Boolean = pending.any { isFresh(it, nowEpochMs) && it.type == type && it.eventId == eventId }

    /**
     * Whether the owner still has to be told that [capture] produced no photo. Once the message went
     * out the request may keep retrying, but the news is not sent again: one guard event is one
     * message, however many times the camera refused.
     */
    fun owesTheOwnerNotice(capture: PendingCapture): Boolean = !capture.announced

    /** The same request, marked as told about, for the queue to hold while it keeps retrying. */
    fun told(capture: PendingCapture): PendingCapture = capture.copy(announced = true)

    /** What the notice field holds once the owner has been told about this request. */
    private const val ANNOUNCED = "1"
}
