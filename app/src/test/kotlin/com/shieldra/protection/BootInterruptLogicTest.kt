package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit E-2: what the app is allowed to claim about a restart. These rules decide whether the owner
 * hears "your phone was rebooted and nothing was watching for four minutes" or hears nothing at all, so
 * they are tested where they can be tested — on the JVM, with no device and no reboot.
 */
class BootInterruptLogicTest {

    private val boot = 1_730_000_000_000L

    @Test
    fun a_reboot_while_armed_is_stamped_and_a_reboot_while_off_is_not() {
        assertEquals(boot, BootInterruptLogic.record(armed = true, nowEpochMs = boot))
        assertEquals(BootInterruptLogic.NO_REBOOT, BootInterruptLogic.record(armed = false, nowEpochMs = boot))
    }

    @Test
    fun the_dark_stretch_is_measured_between_the_boot_and_the_moment_the_app_could_read_it() {
        val interruption = BootInterruptLogic.interruption(
            armed = true,
            rebootedAtEpochMs = boot,
            resumedAtEpochMs = boot + 4 * 60_000L,
        )

        assertEquals(BootInterruption.Interrupted(boot, 4 * 60_000L), interruption)
        assertTrue(BootInterruptLogic.worthReporting(interruption))
    }

    @Test
    fun nothing_is_reported_when_the_owner_had_protection_off() {
        // The owner switched the guards off themselves; a reboot after that interrupted nothing, and
        // sending a message about it would train them to ignore the one that matters.
        val interruption = BootInterruptLogic.interruption(armed = false, boot, boot + 60_000)

        assertEquals(BootInterruption.Nothing, interruption)
        assertFalse(BootInterruptLogic.worthReporting(interruption))
    }

    @Test
    fun nothing_is_reported_when_no_restart_was_recorded() {
        val interruption = BootInterruptLogic.interruption(
            armed = true,
            rebootedAtEpochMs = BootInterruptLogic.NO_REBOOT,
            resumedAtEpochMs = boot,
        )

        assertEquals(BootInterruption.Nothing, interruption)
    }

    @Test
    fun a_clock_that_jumped_backwards_cannot_make_the_dark_period_negative() {
        // The stamp is wall-clock, so a network time correction can put the resume before the boot. The
        // honest answer is that the gap cannot be measured, not that protection was dark for a time
        // that never happened.
        val interruption = BootInterruptLogic.interruption(armed = true, boot, boot - 90_000L)

        assertEquals(BootInterruption.Interrupted(boot, 0L), interruption)
    }

    @Test
    fun a_boot_stamped_in_the_same_millisecond_the_app_read_it_back_is_still_a_restart() {
        val interruption = BootInterruptLogic.interruption(armed = true, boot, boot)

        // Zero darkness is still the fact that matters: the phone came back while armed.
        assertEquals(BootInterruption.Interrupted(boot, 0L), interruption)
        assertTrue(BootInterruptLogic.worthReporting(interruption))
    }
}
