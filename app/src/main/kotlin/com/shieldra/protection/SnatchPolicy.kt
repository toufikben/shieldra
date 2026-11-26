package com.shieldra.protection

/**
 * The dry rules of a confirmed snatch (A-46, owner decision 2026-11-10; A-47 adds instant proof).
 *
 * A confirmed Motion while protection is armed is treated as a snatch until its owner proves
 * otherwise: the screen is locked at once, and for ten minutes the phone asks — visibly, audibly and
 * with vibration — for that proof. Unlocking with the owner's own credential (or pressing the stop
 * button inside the app) is the proof: it clears the whole incident and gives the periodic signal
 * back its chosen interval.
 *
 * A-47 makes that proof immediate rather than a ten-minute wait: the moment the keyguard goes away in
 * a live process the runtime receiver proves ownership, and early checkpoints (20 s, 60 s, 3 min and
 * 5 min after the lock) keep it reachable across process death. The moment is persisted as
 * [Incident.proofSeenAtMs], so a kill between the proof and the teardown cannot resurrect the incident
 * and send evidence the owner already cancelled.
 *
 * No proof inside the window is a theft, and waiting for a wrong PIN first would be waiting for the
 * thief's kindness. The ladder therefore escalates on its own at the tenth minute and then sends full
 * evidence (photo and fix) at the owner's chosen cadence, five or ten minutes, until the phone enters a
 * safe zone or proves ownership. A wrong PIN, when it happens, only accelerates the same ladder, and
 * only at the owner's own lock threshold (A-47).
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

    /**
     * The early ownership checks after the lock (A-47). The 10-minute ladder tick is the ceiling; these
     * make a killed process lose seconds instead of ten minutes, and every one of them runs the same
     * [unlockedSince] rule, so none of them can invent a proof the phone did not give.
     */
    private val PROOF_CHECKPOINTS_MS = longArrayOf(20_000L, 60_000L, 3 * 60_000L, 5 * 60_000L)

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
     *
     * [secureAtBegin] is the phone's own answer at the moment of the snatch: a phone without a
     * credential cannot prove ownership by unlocking, so it is never challenged (A-47). [proofSeenAtMs]
     * is the moment ownership was proven; writing it before the teardown is what makes the teardown
     * idempotent when the process dies in between.
     */
    data class Incident(
        val phase: Phase = Phase.IDLE,
        val eventId: String? = null,
        val startedAtMs: Long = 0L,
        val lockedAtMs: Long = 0L,
        val escalatedAtMs: Long = 0L,
        val ticks: Int = 0,
        val nextDueAtMs: Long = 0L,
        val secureAtBegin: Boolean = false,
        val proofSeenAtMs: Long = 0L,
    ) {
        val active: Boolean get() = phase != Phase.IDLE
    }

    /** A confirmed snatch begins waiting for proof — never capturing, because location at t=0 is noise. */
    fun begin(eventId: String?, nowMs: Long, locked: Boolean, secureAtBegin: Boolean): Incident = Incident(
        phase = Phase.AWAITING_PROOF,
        eventId = eventId,
        startedAtMs = nowMs,
        lockedAtMs = if (locked) nowMs else 0L,
        nextDueAtMs = nowMs + PROOF_WINDOW_MS,
        secureAtBegin = secureAtBegin,
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

    /**
     * Whether this incident can accept an unlock as proof at all (A-47 D8). A phone with no secure lock
     * — or one whose screen never actually locked — has nothing to dismiss, so the challenge is never
     * opened and the unlock receiver never treats a dismissal as proof.
     */
    fun canProveByUnlock(incident: Incident): Boolean =
        incident.active && incident.secureAtBegin && incident.lockedAtMs > 0L

    /** Records the moment ownership was proven, before the teardown that clears the rest (A-47 D6). */
    fun proofSeen(incident: Incident, nowMs: Long): Incident =
        incident.copy(proofSeenAtMs = nowMs)

    /**
     * The wrong-PIN accelerator, narrowed to the owner's own lock threshold (A-47 D7). The receiver
     * counts every attempt; only the attempt that reaches [lockThreshold] moves the ladder. Earlier
     * attempts do nothing extra, and later ones keep the ordinary lock-evidence path so a thief holding
     * the phone cannot turn the ladder into a stream per PIN.
     */
    fun acceleratesOnFailedUnlock(incident: Incident, failedAttempts: Int, lockThreshold: Int): Boolean =
        incident.active && lockThreshold > 0 && failedAttempts == lockThreshold

    /**
     * The next early ownership check after [nowMs], or null when they are spent and the tenth-minute
     * ladder tick is the only clock left. The checks are anchored to the moment the screen actually
     * locked, and never run past the proof window: a late lock simply shifts its own chain.
     */
    fun nextProofCheckMs(incident: Incident, nowMs: Long): Long? {
        if (!incident.active || incident.proofSeenAtMs != 0L) return null
        val anchorMs = if (incident.lockedAtMs > 0L) incident.lockedAtMs else incident.startedAtMs
        val windowEndMs = incident.startedAtMs + PROOF_WINDOW_MS
        return PROOF_CHECKPOINTS_MS
            .asSequence()
            .map { anchorMs + it }
            .firstOrNull { it > nowMs && it < windowEndMs }
    }

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
