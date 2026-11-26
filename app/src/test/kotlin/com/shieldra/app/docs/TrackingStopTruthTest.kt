package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P4-21: a live-tracking trail keeps reporting after the owner stops it.
 *
 * `startTrackingIfEnabled` is careful: it refuses unless the switch is on and the plan allows
 * tracking, and it fixes the tick count from the duration the owner chose. But every later tick is
 * decided by one line — `if (!ProtectionSettingsStore(applicationContext).load().armed) return` —
 * which asks only whether protection is armed. So:
 *
 *  - an owner who switches **live tracking off** while a trail is running (the switch is on the
 *    Protection screen and is his to turn off) keeps having his position sent on every tick until
 *    the window ends, because the tick never re-reads the switch it started from;
 *  - a **lapsed subscription** keeps the trail running for the same reason: the plan is asked once,
 *    at start, and the tier can be rewritten by `PlayProStore` at any time afterwards (P4-20 fixed
 *    exactly this in the remote-command poller).
 *
 * The two are the same omission: a decision read once and never re-asked while it still has effect.
 * A location trail is the most sensitive thing the app does on a schedule, so "off" has to mean off
 * at the next tick, not at the next arming.
 *
 * The fix is the same clause P4-20 added: the tick re-reads the switch and the plan, and stops.
 */
class TrackingStopTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val service: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt").readText()
    }

    /** The body of `scheduleTrackingTick`, where the per-tick decision lives. */
    private fun tickBody(): String {
        val start = service.indexOf("private fun scheduleTrackingTick(")
        assertTrue(start > 0, "the tracking tick is gone, so this finding must be re-measured")
        val end = service.indexOf("\n    private fun ", start + 10)
        return service.substring(start, if (end > start) end else service.length)
    }

    /** The start still gates, so this test measures the tick and not the entry point. */
    @Test
    fun p4_21_the_start_still_gates_on_the_switch_and_the_plan() {
        val start = service.indexOf("private fun startTrackingIfEnabled(")
        assertTrue(start > 0, "the tracking start is gone, so this finding must be re-measured")
        val body = service.substring(start, service.indexOf("\n    private fun ", start + 10))
        assertTrue(
            body.contains("!settings.liveTrackingEnabled"),
            "the start no longer refuses a disabled switch, so this finding must be re-measured",
        )
        assertTrue(
            body.contains("allowsTracking"),
            "the start no longer refuses a plan without tracking, so this finding must be re-measured",
        )
    }

    /** The tick re-reads the owner's switch, so turning it off stops the trail at the next tick. */
    @Test
    fun p4_21_the_tick_stops_when_the_owner_switches_tracking_off() {
        val body = tickBody()
        assertFalse(
            body.contains("load().armed) return@postDelayed"),
            "the tick decides on armed alone, so an owner who switches live tracking off keeps having " +
                "his position sent until the window ends",
        )
        assertTrue(
            body.contains("liveTrackingEnabled"),
            "the tick never re-reads the live-tracking switch it started from",
        )
    }

    /** And the tick re-reads the plan, so a lapsed subscription ends the trail too. */
    @Test
    fun p4_21_the_tick_stops_when_the_plan_stops_allowing_tracking() {
        val body = tickBody()
        assertTrue(
            body.contains("allowsTracking"),
            "the tick never re-asks the plan, so a subscription that lapsed mid-trail keeps sending " +
                "location every tick",
        )
    }

    /** Both conditions are asked on one stored snapshot, not two separate reads. */
    @Test
    fun p4_21_the_tick_asks_one_snapshot() {
        val body = tickBody()
        assertTrue(
            Regex("""val\s+\w+\s*=\s*ProtectionSettingsStore\(applicationContext\)\.load\(\)""").containsMatchIn(body),
            "the tick reads the settings inline, so its two questions can see two different snapshots",
        )
    }
}
