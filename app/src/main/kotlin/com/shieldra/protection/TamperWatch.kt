package com.shieldra.protection

/**
 * One reading of the debugging door. Both values are settings the platform publishes; this file
 * never reads them itself, so the rule stays unit-testable without a device.
 */
data class TamperSnapshot(
    val usbDebugging: Boolean,
    val developerOptions: Boolean,
)

/** A door that opened after protection was armed. */
enum class TamperSignal { UsbDebugging, DeveloperOptions }

/**
 * Reports each debugging door the first time it is seen open after protection was armed (P2-1).
 *
 * USB debugging is how a stolen phone is driven from a laptop, and the developer options behind it
 * are how it is switched on. The state at arming is the baseline: a phone that was already a
 * development device does not alarm for being one, exactly like the SIM guard does not alarm for
 * the card that was in the phone when the owner armed.
 *
 * The guard reads a heartbeat, so the same open door would otherwise be reported every minute. Each
 * signal is reported once per spell: it must be seen closed again before it can be reported again,
 * which is also what makes a switch flipped off and back on a new act rather than a repeat.
 *
 * Not thread-safe on purpose: one instance lives in the protection service and is only touched
 * from its own capture path.
 */
class TamperWatch(baseline: TamperSnapshot) {
    /** The last state observed per signal; a signal is reported only when it leaves its rest state. */
    private val lastSeen = mutableMapOf(
        TamperSignal.UsbDebugging to baseline.usbDebugging,
        TamperSignal.DeveloperOptions to baseline.developerOptions,
    )

    /** The doors that opened since the previous reading, and only those. */
    fun newFindings(now: TamperSnapshot): Set<TamperSignal> {
        val opened = mutableSetOf<TamperSignal>()
        record(TamperSignal.UsbDebugging, now.usbDebugging, opened)
        record(TamperSignal.DeveloperOptions, now.developerOptions, opened)
        return opened
    }

    private fun record(signal: TamperSignal, open: Boolean, opened: MutableSet<TamperSignal>) {
        if (open && lastSeen[signal] != true) opened += signal
        lastSeen[signal] = open
    }
}
