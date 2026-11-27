package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P4-24: the battery guard's switch is read once, when the receiver is registered, and never again.
 *
 * `startBatteryGuard` is the only place that asks `batteryEnabled`, and it does so to decide whether
 * to register the receiver at all:
 *
 * ```
 * if (batteryReceiver != null) return
 * if (!ProtectionSettingsStore(applicationContext).load().batteryEnabled) return
 * ```
 *
 * `onReceive` then never re-reads the switch. Both directions are broken, and both are the owner's
 * own setting on the Protection screen:
 *
 *  - **off does not stop it.** An owner who switches the battery guard off while protection is armed
 *    keeps the receiver, so the "last breath" capture still fires at the threshold — a guard doing
 *    the work it was switched off from doing, and sending an alert about a phone whose owner said
 *    this alert was not wanted.
 *  - **on does not start it.** `ProtectionRuntime.applySettings` (the Save path) never calls
 *    `startBatteryGuard`, so an owner who switches it on while armed sees the switch on and gets no
 *    capture at all until the service restarts. The screen and the guard disagree.
 *
 * Every other guard in this service re-reads its switch where it acts: `checkSim` asks `simEnabled`
 * on each pass, `checkTamper` asks `armed`, `maybeEscalate` asks `escalationEnabled`, and the
 * tracking tick now asks both the switch and the plan (P4-21). The battery guard is the one that
 * decided once and forgot. The fix is the same shape: register the receiver for the life of the
 * service and ask the switch inside `onReceive`.
 */
class BatteryGuardSwitchTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val service: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt").readText()
    }

    /** The body of `startBatteryGuard`, where the registration decision lives. */
    private fun startBody(): String {
        val start = service.indexOf("private fun startBatteryGuard(")
        assertTrue(start > 0, "the battery guard is gone, so this finding must be re-measured")
        val end = service.indexOf("\n    override fun onStartCommand(", start)
        return service.substring(start, if (end > start) end else service.length)
    }

    /** The receiver's body, where each battery tick decides whether it may capture. */
    private fun receiveBody(): String {
        val start = service.indexOf("private fun startBatteryGuard(")
        assertTrue(start > 0, "the battery guard is gone, so this finding must be re-measured")
        val body = startBody()
        val inner = body.indexOf("override fun onReceive(")
        assertTrue(inner > 0, "the battery receiver no longer has a body to measure")
        return body.substring(inner)
    }

    /** The reference guard still re-reads its switch, so this finding is about the battery one only. */
    @Test
    fun p4_24_the_sim_guard_still_re_reads_its_switch_where_it_acts() {
        val start = service.indexOf("private fun checkSim(")
        assertTrue(start > 0, "the SIM guard is gone, so this finding must be re-measured")
        val body = service.substring(start, service.indexOf("\n    /**", start))
        assertTrue(
            body.contains("simEnabled"),
            "the SIM guard stopped re-reading its switch, so the shape this finding relies on is gone",
        )
    }

    /** The registration no longer refuses, so a switch turned on while armed is honoured. */
    @Test
    fun p4_24_the_receiver_is_registered_regardless_of_the_switch() {
        val body = startBody()
        assertFalse(
            body.contains("if (!ProtectionSettingsStore(applicationContext).load().batteryEnabled) return"),
            "the battery receiver is registered only when the switch was on at start, so an owner who " +
                "switches the guard on while armed gets nothing until the service restarts",
        )
    }

    /** And each tick asks the switch, so switching it off stops the capture at once. */
    @Test
    fun p4_24_each_battery_tick_asks_the_switch() {
        val body = receiveBody()
        assertTrue(
            body.contains("batteryEnabled"),
            "a battery tick captures without asking the switch, so an owner who switched the guard " +
                "off while armed still gets a last-breath alert",
        )
    }
}
