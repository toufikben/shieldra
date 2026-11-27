package com.shieldra.protection

import android.content.Context
import android.content.SharedPreferences

/**
 * Where an open wipe window lives (P2-3). It survives a process death on purpose: the owner sends
 * `/wipe` from a phone that may then be rebooted by whoever holds it, and a request that vanished with
 * the poller thread would make the confirmation that follows meaningless.
 *
 * It is its own preferences file, for the reason A-44 taught: a settings `Save` rewrites a whole
 * snapshot and must never be able to overwrite a live decision made somewhere else.
 *
 * Nothing here erases anything. It stores two timestamps and is cleared by a confirmation, a cancel, an
 * expiry, or the owner proving the phone is his.
 */
class RemoteWipeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE)

    /** The request waiting for its confirmation, or null when nothing is open. */
    fun read(): RemoteWipeRequest.Open? {
        if (!prefs.contains(KEY_REQUESTED_AT) || !prefs.contains(KEY_EXPIRES_AT)) return null
        return RemoteWipeRequest.Open(
            requestedAtMs = prefs.getLong(KEY_REQUESTED_AT, 0L),
            expiresAtMs = prefs.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    fun write(open: RemoteWipeRequest.Open) {
        prefs.edit()
            .putLong(KEY_REQUESTED_AT, open.requestedAtMs)
            .putLong(KEY_EXPIRES_AT, open.expiresAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_REQUESTED_AT).remove(KEY_EXPIRES_AT).apply()
    }

    private companion object {
        const val PREFERENCE = "shieldra_remote_wipe"
        const val KEY_REQUESTED_AT = "wipe_requested_at"
        const val KEY_EXPIRES_AT = "wipe_expires_at"
    }
}
