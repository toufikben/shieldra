package com.shieldra.protection

import android.content.Context

/**
 * Where a snatch survives its own app process (A-46).
 *
 * It lives in device-protected storage because the one moment it must not lose is the reboot: a thief
 * who restarts the phone gets a locked screen and no credential, and the app may only be able to read
 * this side of storage then. The file holds no evidence and no owner text — a phase, an event id and
 * the clocks the ladder needs — so a phone sitting at the lock screen exposes nothing but the fact
 * that protection is dealing with an incident.
 *
 * It is separate from [ProtectionSettings] for the reason A-44 taught: a settings Save rewrites a whole
 * snapshot and must never be able to overwrite a live decision made somewhere else.
 */
class SnatchStore(context: Context) {

    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(): SnatchPolicy.Incident {
        if (!prefs.contains(KEY_PHASE)) return SnatchPolicy.Incident()
        val phase = runCatching {
            SnatchPolicy.Phase.valueOf(prefs.getString(KEY_PHASE, "").orEmpty())
        }.getOrDefault(SnatchPolicy.Phase.IDLE)
        return SnatchPolicy.Incident(
            phase = phase,
            eventId = prefs.getString(KEY_EVENT_ID, null),
            startedAtMs = prefs.getLong(KEY_STARTED_AT, 0L),
            lockedAtMs = prefs.getLong(KEY_LOCKED_AT, 0L),
            escalatedAtMs = prefs.getLong(KEY_ESCALATED_AT, 0L),
            ticks = prefs.getInt(KEY_TICKS, 0),
            nextDueAtMs = prefs.getLong(KEY_NEXT_DUE, 0L),
        )
    }

    fun write(incident: SnatchPolicy.Incident) {
        prefs.edit()
            .putString(KEY_PHASE, incident.phase.name)
            .putString(KEY_EVENT_ID, incident.eventId)
            .putLong(KEY_STARTED_AT, incident.startedAtMs)
            .putLong(KEY_LOCKED_AT, incident.lockedAtMs)
            .putLong(KEY_ESCALATED_AT, incident.escalatedAtMs)
            .putInt(KEY_TICKS, incident.ticks)
            .putLong(KEY_NEXT_DUE, incident.nextDueAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val NAME = "shieldra_snatch"
        const val KEY_PHASE = "phase"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_LOCKED_AT = "locked_at"
        const val KEY_ESCALATED_AT = "escalated_at"
        const val KEY_TICKS = "ticks"
        const val KEY_NEXT_DUE = "next_due_at"
    }
}
