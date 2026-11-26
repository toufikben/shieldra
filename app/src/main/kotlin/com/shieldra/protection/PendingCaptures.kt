package com.shieldra.protection

import android.content.Context
import android.content.SharedPreferences
import com.shieldra.domain.SecurityEventType

/**
 * The queue of captures that could not run, in app-private preferences the backup rules already
 * exclude. Nothing is stored here but which guard asked, for which incident, and when — no media, no
 * position, no secret — because this file is written on the worst possible path, where the phone is
 * in someone else's hands and the camera could not be reached.
 *
 * Held outside the database on purpose: a capture request arrives exactly when the storage may still
 * be recovering, and a queue that needed the database to survive would be no queue at all.
 */
class PendingCaptures(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** What is waiting, in the order it was written; undecodable lines are simply not there. */
    fun all(): List<PendingCapture> = lines().mapNotNull(PendingCaptureLogic::decode)

    /**
     * Writes [type] down as a request that still has to run.
     *
     * @param announced whether the owner has already been told in text that this request got no photo,
     *   so a retry does not send the same bad news twice.
     * @return false when the same guard and incident was already waiting, so the caller can tell
     *   "this is now queued" from "this added nothing" and only announce a fresh problem.
     */
    @Synchronized
    fun add(
        type: SecurityEventType,
        eventId: String? = null,
        failedAttempts: Int? = null,
        announced: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val waiting = all()
        if (PendingCaptureLogic.holds(waiting, type, eventId, nowMs)) return false
        val request = PendingCapture(type, eventId, failedAttempts, nowMs, announced)
        val next = PendingCaptureLogic.plus(waiting, request)
        prefs.edit().putStringSet(KEY_PENDING, next.map(PendingCaptureLogic::encode).toSet()).apply()
        return true
    }

    /**
     * Puts a request the caller has taken back into the queue with [PendingCapture.requestedAtEpochMs]
     * and [PendingCapture.announced] untouched.
     *
     * A retry that lost its moment would be a new request, and a new request would be a new message to
     * the owner. Keeping the original entry keeps both the expiry clock and the notice honest.
     *
     * @return false when the same guard and incident is already waiting again.
     */
    @Synchronized
    fun put(capture: PendingCapture): Boolean {
        val waiting = all()
        if (PendingCaptureLogic.holds(waiting, capture.type, capture.eventId, capture.requestedAtEpochMs)) return false
        val next = PendingCaptureLogic.plus(waiting, capture)
        prefs.edit().putStringSet(KEY_PENDING, next.map(PendingCaptureLogic::encode).toSet()).apply()
        return true
    }

    /**
     * Takes everything the queue was holding and leaves nothing behind: the requests that may still
     * run come back as [Drain.due], the ones past their moment as [Drain.expired] for the caller to
     * report in text. Both are deleted, because keeping a request that will never run is a promise the
     * app has no intention of honouring.
     *
     * Only the lines this call read are removed, so a request that arrives while the caller is running
     * its captures is never lost.
     */
    @Synchronized
    fun take(nowMs: Long = System.currentTimeMillis()): Drain {
        val written = lines()
        if (written.isEmpty()) return Drain(emptyList(), emptyList())
        val split = PendingCaptureLogic.split(written.mapNotNull(PendingCaptureLogic::decode), nowMs)
        // Whatever the queue holds now, minus exactly the lines this call read, stays behind — so a
        // request that arrived while the caller was working is never taken away before it ran.
        val stillWaiting = lines() - written
        prefs.edit().putStringSet(KEY_PENDING, stillWaiting).apply()
        return split
    }

    /** Forgets the whole queue: protection is off, so nobody is to be photographed. */
    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    private fun lines(): Set<String> =
        prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

    private companion object {
        const val PREFERENCES_NAME = "shieldra_pending_captures"
        const val KEY_PENDING = "pending_captures"
    }
}
