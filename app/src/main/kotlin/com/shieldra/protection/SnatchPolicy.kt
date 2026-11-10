package com.shieldra.protection

/**
 * The dry rules of a confirmed snatch (A-46, owner decision 2026-11-10).
 *
 * A confirmed Motion while protection is armed is treated as a snatch until its owner proves
 * otherwise: the screen is locked at once, and for ten minutes the phone asks — visibly, audibly and
 * with vibration — for that proof. Unlocking with the owner's own credential (or pressing the stop
 * button inside the app) is the proof: it clears the whole incident and gives the periodic signal
 * back its chosen interval.
 *
 * No proof inside the window is a theft, and waiting for a wrong PIN first would be waiting for the
 * thief's kindness. The ladder therefore escalates on its own at the tenth minute and then sends full
 * evidence (photo and fix) at the owner's chosen cadence, five or ten minutes, until the phone enters a
 * safe zone or proves ownership. A wrong PIN, when it happens, only accelerates the same ladder.
 *
 * At or below ten percent battery and unplugged the ladder deliberately slows to its longest step: the
 * last percent is worth more as a signal than as a stream of photos (the 10% agreement, A-33). Six
 * hours is the ceiling — a phone that survived that long is a police matter, not a notification stuck
 * forever.
 *
 * Pure and Android-free, so every one of those transitions is stated without a device.
 */
object SnatchPolicy {

    /** Ten minutes of visible, audible and vibrating proof before the ladder escalates (owner decision). */
    const val PROOF_WINDOW_MS = 10 * 60_000L

    /** The owner's two cadence choices for the escalating ladder. */
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 10
    const val DEFAULT_INTERVAL_MINUTES = 5

    /** The same two choices, in the order a screen offers them. */
    val CHOICES_MINUTES = listOf(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    /** At or below this level, unplugged, the ladder slows down (the 10% agreement in A-33). */
    const val BATTERY_FLOOR_PERCENT = 10

    /** No renewal past this point: see the class note. */
    const val INCIDENT_CAP_MS = 6 * 60 * 60_000L

    /**
     * The keyguard cannot be read as proof of ownership before this much time after the lock: a phone
     * that has not yet turned its screen off would otherwise report "unlocked" to itself.
     */
    const val UNLOCK_PROOF_GRACE_MS = 15_000L

    enum class Phase { IDLE, AWAITING_PROOF, ESCALATING }

    /**
     * One snatch, as it has to survive a dead process and a reboot: the phase, when it started, when
     * the screen was actually locked, and when the next tick is due. [lockedAtMs] is zero when the
     * lock power was not available yet — the tick keeps trying, so granting the policy mid-incident
     * takes effect at the next attempt instead of at the next snatch.
     */
    data class Incident(
        val phase: Phase = Phase.IDLE,
        val eventId: String? = null,
        val startedAtMs: Long = 0L,
        val lockedAtMs: Long = 0L,
        val escalatedAtMs: Long = 0L,
        val ticks: Int = 0,
        val nextDueAtMs: Long = 0L,
    ) {
        val active: Boolean get() = phase != Phase.IDLE
    }

    /** A confirmed snatch begins waiting for proof — never capturing, because location at t=0 is noise. */
    fun begin(eventId: String?, nowMs: Long, locked: Boolean): Incident = Incident(
        phase = Phase.AWAITING_PROOF,
        eventId = eventId,
        startedAtMs = nowMs,
        lockedAtMs = if (locked) nowMs else 0L,
        nextDueAtMs = nowMs + PROOF_WINDOW_MS,
    )

    /** Moves the ladder to full evidence now and sets the next send. */
    fun escalate(incident: Incident, nowMs: Long, intervalMs: Long): Incident = incident.copy(
        phase = Phase.ESCALATING,
        escalatedAtMs = if (incident.escalatedAtMs == 0L) nowMs else incident.escalatedAtMs,
        ticks = incident.ticks + 1,
        nextDueAtMs = nowMs + intervalMs,
    )

    /** Keeps the incident alive without sending: a safe-zone pause, or a tick with nothing to do. */
    fun postpone(incident: Incident, nowMs: Long, intervalMs: Long): Incident =
        incident.copy(nextDueAtMs = nowMs + intervalMs)

    /** Proof of ownership clears the whole incident — phase, event, ticks, everything. */
    fun proofConfirmed(incident: Incident): Incident = Incident()

    /**
     * Whether the owner's own credential has been used since the snatch locked the phone. A phone that
     * was never locked cannot prove anything this way; the in-app button remains the other path.
     */
    fun unlockedSince(incident: Incident, nowMs: Long, keyguardLocked: Boolean): Boolean =
        incident.lockedAtMs > 0L &&
            nowMs - incident.lockedAtMs >= UNLOCK_PROOF_GRACE_MS &&
            !keyguardLocked

    /** The six-hour ceiling: the ladder stops renewing itself. */
    fun expired(incident: Incident, nowMs: Long): Boolean =
        incident.active && nowMs - incident.startedAtMs >= INCIDENT_CAP_MS

    /**
     * How far apart the escalating sends are. The owner's choice is clamped to the two offered values;
     * at or below the battery floor, unplugged, the longest step wins so the last percent is not spent
     * on a burst.
     */
    fun intervalMs(configuredMinutes: Int, batteryPercent: Int?, charging: Boolean): Long {
        val chosen = configuredMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        val strained = batteryPercent != null && batteryPercent <= BATTERY_FLOOR_PERCENT && !charging
        return (if (strained) MAX_INTERVAL_MINUTES else chosen) * 60_000L
    }
}
