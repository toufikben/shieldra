package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-25: a grace response fires after the owner switched the Motion guard off.
 *
 * `GRACE_THEN_SIREN` waits a few seconds before it does anything, so the owner can pick up his own
 * phone and cancel it. The wait is a `Handler.postDelayed` runnable that calls `fire(...)`, and `fire`
 * asks exactly one live question — `quietInSafeZone` — before it starts the siren, hands the incident
 * to `SnatchCoordinator` and asks the service for a photo. It never asks whether the guard is still
 * on, or whether protection is still armed.
 *
 * `ProtectionRuntime.disarm` does cancel a pending response, so the disarm path is covered. The
 * switch is not: `applySettings` stops Motion monitoring when the owner turns the guard off
 * (`MotionRuntime.stopMonitoring`), and the settings screen does the same, but neither cancels
 * [TableModeResponder]. So a Motion confirmed a moment before the owner switched the guard off still
 * sounds the siren, still opens a snatch window, and still takes a photo — for a guard the owner has
 * already turned off. The same holds for a plan downgrade: the guard keeps its evidence, but a paid
 * response is not something a free plan should still be running.
 *
 * Every other delayed actor in this app re-reads its switch where it acts: the tracking tick (P4-21),
 * the countdown (P4-23) and the battery receiver (P4-24). This is the last one that does not.
 */
class MotionGraceSwitchTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val source: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/TableModeResponder.kt").readText()
    }

    /** The body of `fire`, where the delayed response actually does its work. */
    private fun fireBody(): String {
        val start = source.indexOf("private fun fire(")
        assertTrue(start > 0, "the response is gone, so this finding must be re-measured")
        val end = source.indexOf("\n    /**", start)
        return source.substring(start, if (end > start) end else source.length)
    }

    /** The entry point still reads the owner's response plan, so this measures the delayed path. */
    @Test
    fun p4_25_the_entry_point_still_reads_the_response_plan() {
        val start = source.indexOf("fun onMotionConfirmed(")
        assertTrue(start > 0, "the Motion entry point is gone, so this finding must be re-measured")
        val body = source.substring(start, source.indexOf("\n    @Synchronized", start))
        assertTrue(
            body.contains("MotionResponsePlan.of("),
            "the entry point no longer reads the owner's response, so this finding must be re-measured",
        )
    }

    /** The delayed response asks the guard switch before it sounds or captures. */
    @Test
    fun p4_25_the_delayed_response_asks_the_motion_switch() {
        val body = fireBody()
        assertTrue(
            body.contains("motionEnabled"),
            "the delayed response fires without asking the Motion switch, so an owner who turned the " +
                "guard off during the grace window still gets a siren, a snatch window and a photo",
        )
    }

    /** And it asks whether protection is still armed, because a grace window outlives a disarm. */
    @Test
    fun p4_25_the_delayed_response_asks_whether_protection_is_still_armed() {
        val body = fireBody()
        assertTrue(
            Regex("""\.armed\b""").containsMatchIn(body),
            "the delayed response never reads the armed flag, so the only thing standing between a " +
                "disarm and a siren is one cancel call that a settings Save skips",
        )
    }
}
