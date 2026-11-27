package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-32: the grace window carries a siren verdict that was decided before the window opened.
 *
 * `onMotionConfirmed` reads the owner's response plan once, at the moment the movement is confirmed,
 * and `scheduleAfterGrace` hands `plan.sirenAfterGrace` to `fire` as a plain `Boolean`. The runnable
 * then waits the owner's grace seconds — up to thirty — before it acts.
 *
 * During that wait the owner can still change the response on the Protection screen, and that screen
 * saves straight to the store. P4-25 taught `fire` to re-read the guard switch and the armed flag, but
 * not the response itself, so a plan the owner changed from `GRACE_THEN_SIREN` to `SILENT` inside the
 * window still sounds the siren: the one thing "silent" is named for.
 *
 * The value is not merely stale in a local — `fire` never asks again, so no other path can correct it.
 * Every other delayed actor asks its own setting where it acts: the tracking tick (P4-21), the
 * countdown (P4-23), the battery receiver (P4-24), and `fire` itself for the switch and the armed flag
 * (P4-25). The response is the last decision in this file that is carried instead of re-read.
 */
class MotionPlanAtFireTruthTest {

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

    /** The grace scheduler, which decides what the delayed runnable is told to do. */
    private fun graceBody(): String {
        val start = source.indexOf("private fun scheduleAfterGrace(")
        assertTrue(start > 0, "the grace window is gone, so this finding must be re-measured")
        val end = source.indexOf("\n    private fun fire(", start)
        return source.substring(start, if (end > start) end else source.length)
    }

    /** The premise: the entry point still reads the plan, so the plan is what this measures. */
    @Test
    fun p4_32_the_entry_point_still_reads_the_response_plan() {
        val start = source.indexOf("fun onMotionConfirmed(")
        assertTrue(start > 0, "the Motion entry point is gone, so this finding must be re-measured")
        val body = source.substring(start, source.indexOf("\n    @Synchronized", start))
        assertTrue(
            body.contains("MotionResponsePlan.of("),
            "the entry point no longer reads the owner's response, so this finding must be re-measured",
        )
    }

    /** And the response is asked again where it acts, after the window, not before it. */
    @Test
    fun p4_32_the_response_is_asked_again_where_it_acts() {
        val body = fireBody()
        assertTrue(
            body.contains("MotionResponsePlan.of("),
            "the delayed response runs on the siren verdict it was handed before the grace window, so " +
                "an owner who switched the response to SILENT inside the window still gets a siren",
        )
    }

    /** The window hands over which phase it is in, not a siren verdict decided earlier. */
    @Test
    fun p4_32_the_window_does_not_carry_a_decided_siren() {
        val body = graceBody()
        assertTrue(
            !body.contains("siren ="),
            "the grace window still passes a siren verdict it computed before the wait, so the plan " +
                "cannot be re-read when the response finally runs",
        )
    }
}
