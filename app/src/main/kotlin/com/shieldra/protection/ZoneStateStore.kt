package com.shieldra.protection

import android.content.Context

/** One reading of "is the phone inside a safe zone", with the clock that came with it. */
data class ZoneSample(val inside: Boolean, val atMs: Long) {

    /**
     * Fresh means recent, and not from a clock that jumped backwards. A sample written after the device
     * clock moved on is refused rather than trusted, because the ladder reads this to decide whether to
     * make any noise at all.
     */
    fun isFresh(nowMs: Long, staleAfterMs: Long): Boolean {
        if (nowMs < atMs) return false
        return nowMs - atMs <= staleAfterMs
    }
}

/**
 * Where the phone last was, written by the safe-zone sampler and read by the Motion ladder.
 *
 * It lives in its own preferences file, not in the protection settings, for the reason A-44 taught:
 * a settings `Save` rewrites a whole snapshot and must never be able to overwrite a live decision made
 * somewhere else. The sampler has exactly one writer here and the ladder only reads.
 */
class ZoneStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE)

    fun write(inside: Boolean, atMs: Long) {
        prefs.edit().putBoolean(KEY_INSIDE, inside).putLong(KEY_AT_MS, atMs).apply()
    }

    /** The last reading, or null when this install has never completed one. */
    fun read(): ZoneSample? {
        if (!prefs.contains(KEY_AT_MS) || !prefs.contains(KEY_INSIDE)) return null
        return ZoneSample(
            inside = prefs.getBoolean(KEY_INSIDE, false),
            atMs = prefs.getLong(KEY_AT_MS, 0L),
        )
    }

    fun clear() {
        prefs.edit().remove(KEY_INSIDE).remove(KEY_AT_MS).apply()
    }

    private companion object {
        const val PREFERENCE = "shieldra_zone_state"
        const val KEY_INSIDE = "zone_inside"
        const val KEY_AT_MS = "zone_at_ms"
    }
}
