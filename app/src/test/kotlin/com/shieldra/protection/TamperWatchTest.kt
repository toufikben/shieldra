package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2-1: the tamper watch reports the debugging door opening after protection was armed.
 *
 * A thief who wants to wipe or drive a stolen phone turns on USB debugging (or the developer
 * options behind it). The owner's own settings at the moment of arming are the baseline, so a
 * phone that was already a development device does not alarm for being one.
 *
 * Each finding is reported once: a heartbeat every minute must not turn one switch into sixty
 * alerts. A signal that goes back to its armed state and then returns is a new act, so it is
 * reported again.
 */
class TamperWatchTest {
    @Test
    fun a_switch_already_on_when_protection_armed_is_not_a_finding() {
        val watch = TamperWatch(TamperSnapshot(usbDebugging = true, developerOptions = true))

        assertTrue(watch.newFindings(TamperSnapshot(usbDebugging = true, developerOptions = true)).isEmpty())
    }

    @Test
    fun switching_debugging_on_after_arming_is_reported_once() {
        val watch = TamperWatch(TamperSnapshot(usbDebugging = false, developerOptions = false))
        val turnedOn = TamperSnapshot(usbDebugging = true, developerOptions = false)

        assertEquals(setOf(TamperSignal.UsbDebugging), watch.newFindings(turnedOn))
        assertTrue(watch.newFindings(turnedOn).isEmpty(), "one switch is one finding, not one per heartbeat")
    }

    @Test
    fun a_switch_that_returns_and_comes_back_is_a_new_act() {
        val watch = TamperWatch(TamperSnapshot(usbDebugging = false, developerOptions = false))

        watch.newFindings(TamperSnapshot(usbDebugging = true, developerOptions = false))
        watch.newFindings(TamperSnapshot(usbDebugging = false, developerOptions = false))

        assertEquals(
            setOf(TamperSignal.UsbDebugging),
            watch.newFindings(TamperSnapshot(usbDebugging = true, developerOptions = false)),
        )
    }

    @Test
    fun the_developer_options_switch_is_reported_on_its_own() {
        val watch = TamperWatch(TamperSnapshot(usbDebugging = false, developerOptions = false))

        assertEquals(
            setOf(TamperSignal.DeveloperOptions),
            watch.newFindings(TamperSnapshot(usbDebugging = false, developerOptions = true)),
        )
    }

    @Test
    fun both_switches_arriving_together_are_two_findings_in_one_report() {
        val watch = TamperWatch(TamperSnapshot(usbDebugging = false, developerOptions = false))

        assertEquals(
            setOf(TamperSignal.UsbDebugging, TamperSignal.DeveloperOptions),
            watch.newFindings(TamperSnapshot(usbDebugging = true, developerOptions = true)),
        )
    }
}
