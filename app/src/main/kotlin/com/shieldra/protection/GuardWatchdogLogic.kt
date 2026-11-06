package com.shieldra.protection

/**
 * Whether the guards have gone quiet, decided from two stored times and a window — no context, no
 * Android, nothing to run on a phone (audit development أ).
 *
 * The reason this exists is the worst failure mode in this product class, and it is silent: on the
 * owner's skin a foreground service is killed by the battery manager days before anything is stolen,
 * and nothing announces it. The service stamps [ProtectionSettingsStore.markCheck] on every real
 * cycle, so the stamp is the proof of life; a stamp that stopped is the only evidence that the guard
 * stopped. The watchdog alarm is deliberately *independent* of the service — an alarm the service
 * itself scheduled and then died would never fire, so the question is asked by a separate process
 * waking up on the wall clock.
 *
 * The window is generous on purpose. The claim being made is not "a minute was missed"; it is
 * "protection may be off", and an alarm that fires because the phone sat in Doze for twenty minutes
 * teaches the owner to ignore the alarm that fires because the guard was killed.
 *
 * A clock moved backwards is the one reading this cannot be trusted for: a thief who sets the clock
 * back makes the silence look shorter than it was. The window still catches the common case, because
 * `now` comes from the same wall clock the heartbeat was written with, and the stamp cannot be
 * rewritten by a thief who does not know the exact key. This is stated rather than solved because
 * solving it needs a second clock that survives a reboot, which a locked-down phone does not offer.
 */
object GuardWatchdogLogic {
    /** How long the guards may stay silent before the owner is told, in minutes. */
    const val TOLERANCE_MINUTES = 30L

    sealed interface Verdict {
        /** A heartbeat landed inside the window: the guard is alive and nothing is sent. */
        data object Watching : Verdict

        /**
         * Silent, and this spell of silence has already been reported. Reported once per spell, so a
         * guard that stays dead does not send an alert every thirty minutes and a guard that comes
         * back and dies again is heard about again.
         */
        data object AlreadyReported : Verdict

        /** Silent past the window. [everRan] separates "the service never started" from "it stopped". */
        data class Silent(val minutesSilent: Long, val everRan: Boolean) : Verdict
    }

    /**
     * @param lastCheckEpochMs the last real guard cycle, or 0 when the guards never stamped a check.
     * @param lastReportedEpochMs when a silence was last reported, or 0 when none ever was.
     * @param nowEpochMs the wall clock at the firing.
     */
    fun decide(
        lastCheckEpochMs: Long,
        lastReportedEpochMs: Long,
        nowEpochMs: Long,
        toleranceMs: Long = TOLERANCE_MINUTES * 60_000L,
    ): Verdict {
        val silentFor = nowEpochMs - lastCheckEpochMs
        if (silentFor < toleranceMs) return Verdict.Watching
        // A report belongs to the silence it was written into: once the stamp moves past it, the old
        // silence ended and any new one is worth telling the owner about.
        if (lastReportedEpochMs != 0L && lastReportedEpochMs >= lastCheckEpochMs) return Verdict.AlreadyReported
        return Verdict.Silent(
            minutesSilent = (silentFor / 60_000L).coerceAtLeast(1L),
            everRan = lastCheckEpochMs != 0L,
        )
    }

    /**
     * The next firing time, measured from [nowEpochMs]. There is deliberately no stored deadline to
     * resume from: the watchdog asks the same question every window, and a stored deadline would be a
     * second clock to keep honest for no gain.
     */
    fun nextFireAt(nowEpochMs: Long, toleranceMs: Long = TOLERANCE_MINUTES * 60_000L): Long =
        nowEpochMs + toleranceMs
}
