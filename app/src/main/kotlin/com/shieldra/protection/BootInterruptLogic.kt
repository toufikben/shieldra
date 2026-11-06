package com.shieldra.protection

/**
 * What a restart did to the guards (audit E-2), decided from what the app wrote before the reboot and
 * after it — no Android in sight, so the rules can be tested on the JVM.
 *
 * The reason this needs its own file is the lock screen. A phone that restarts stays encrypted until
 * the owner unlocks it once: the database cannot be opened, the delivery secrets cannot be decrypted,
 * and on Android 14+ a boot receiver is not allowed to start the camera-capable foreground service at
 * all. So the honest thing to do before that first unlock is to *remember* that the phone came back
 * while protection was armed, and to say so the moment saying it becomes possible.
 */
sealed interface BootInterruption {

    /** Nothing to report: the owner had protection off, or no restart was recorded. */
    data object Nothing : BootInterruption

    /**
     * The phone restarted while [ProtectionSettings.armed] was on.
     *
     * [rebootedAtEpochMs] is when the app noticed the boot; [darkMs] is how long the guards could not
     * run for, measured between that stamp and the moment the record was read back. It is never
     * negative, because a clock that jumped backwards cannot make a dark period shorter than nothing.
     */
    data class Interrupted(val rebootedAtEpochMs: Long, val darkMs: Long) : BootInterruption
}

/**
 * The two rules behind the restart record: when a boot is worth remembering, and what it means once
 * the app is awake again.
 */
object BootInterruptLogic {

    /** The stored value that means "no restart was recorded". */
    const val NO_REBOOT = 0L

    /**
     * The stamp to keep for a boot at [nowEpochMs]: the moment itself when protection was armed, and
     * [NO_REBOOT] when it was not. A phone that restarted while the owner had already switched the
     * guards off interrupted nothing, and reporting one would teach the owner to ignore the message.
     */
    fun record(armed: Boolean, nowEpochMs: Long): Long = if (armed) nowEpochMs else NO_REBOOT

    /**
     * What the app should now say about the restart, read at [resumedAtEpochMs].
     *
     * Both facts come from device-protected storage, which is the only storage a locked-but-booted
     * phone can read, so they may disagree with the credential-protected settings after an upgrade: an
     * arm that predates the mirror leaves [armed] false here, which reports nothing rather than
     * inventing an interruption nobody asked about.
     */
    fun interruption(
        armed: Boolean,
        rebootedAtEpochMs: Long,
        resumedAtEpochMs: Long,
    ): BootInterruption {
        if (!armed || rebootedAtEpochMs == NO_REBOOT) return BootInterruption.Nothing
        val dark = resumedAtEpochMs - rebootedAtEpochMs
        return BootInterruption.Interrupted(rebootedAtEpochMs, if (dark < 0) 0L else dark)
    }

    /** Whether the restart is worth a message to the owner. */
    fun worthReporting(interruption: BootInterruption): Boolean = interruption is BootInterruption.Interrupted
}
