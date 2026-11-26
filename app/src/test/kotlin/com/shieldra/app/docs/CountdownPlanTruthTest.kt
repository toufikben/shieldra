package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-23: a countdown cycle still fires once after the plan stops allowing it.
 *
 * The countdown is a paid feature (`TierGate.allowsCountdown`), and `CountdownScheduler.start` is
 * careful about it: it refuses a disabled switch and a plan without the countdown, so an owner who
 * never bought it never gets a cycle. Two other paths reach the same alarms and never ask:
 *
 *  - `CountdownReceiver` fires `ACTION_EXPIRED` and captures *before* anything re-arms. The alarm
 *    that is already in the clock does not know the plan changed, so a subscription that lapses
 *    while a cycle is armed still sends one full capture-and-alert — a paid feature doing its paid
 *    work on the free plan. `restart()` then refuses, so the leak is one message per armed cycle.
 *  - `rescheduleAfterBoot` re-arms the stored deadline when it is still in the future, and fires the
 *    cycle itself when the phone was off past it, on `countdownEnabled` alone. So every reboot of a
 *    lapsed phone repeats the leak, and the stored deadline is never cleared: the alarms keep coming
 *    back even though nothing may re-arm them.
 *
 * `postSnoozeNotice` has the same omission on the quiet path: it posts the owner-facing "snooze the
 * countdown" notice for a plan that no longer has a countdown to snooze.
 *
 * The fix is the same clause P4-20 and P4-21 added: ask the plan at the moment the thing happens,
 * not only at the moment it was armed, and clear what the plan no longer allows.
 */
class CountdownPlanTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val source: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/CountdownReceiver.kt").readText()
    }

    /** The receiver's body, where a fired cycle decides whether it may capture. */
    private fun receiverBody(): String {
        val start = source.indexOf("class CountdownReceiver")
        assertTrue(start > 0, "the countdown receiver is gone, so this finding must be re-measured")
        val end = source.indexOf("companion object", start)
        return source.substring(start, if (end > start) end else source.length)
    }

    /** The boot path, where a stored deadline is trusted without asking who may still run it. */
    private fun bootBody(): String {
        val start = source.indexOf("fun rescheduleAfterBoot(")
        assertTrue(start > 0, "the boot re-arm is gone, so this finding must be re-measured")
        val end = source.indexOf("\n    fun cancel(", start)
        return source.substring(start, if (end > start) end else source.length)
    }

    /** The quiet path, where the owner is invited to snooze a countdown he may no longer have. */
    private fun snoozeBody(): String {
        val start = source.indexOf("fun postSnoozeNotice(")
        assertTrue(start > 0, "the snooze notice is gone, so this finding must be re-measured")
        val end = source.indexOf("\n    private fun ", start)
        return source.substring(start, if (end > start) end else source.length)
    }

    /** The entry point still gates, so this test measures the paths that bypass it. */
    @Test
    fun p4_23_the_start_still_gates_on_the_switch_and_the_plan() {
        val start = source.indexOf("fun start(context: Context)")
        assertTrue(start > 0, "the countdown start is gone, so this finding must be re-measured")
        val body = source.substring(start, source.indexOf("\n    /** Re-arms", start))
        assertTrue(
            body.contains("!settings.countdownEnabled"),
            "the start no longer refuses a disabled switch, so this finding must be re-measured",
        )
        assertTrue(
            body.contains("allowsCountdown"),
            "the start no longer refuses a plan without the countdown, so this finding must be re-measured",
        )
    }

    /** A cycle that fires asks the plan before it captures. */
    @Test
    fun p4_23_a_fired_cycle_asks_the_plan_before_it_captures() {
        val body = receiverBody()
        assertTrue(
            body.contains("allowsCountdown"),
            "the receiver fires the capture without asking the plan, so a subscription that lapsed " +
                "while a cycle was armed still sends one paid capture on the free plan",
        )
    }

    /** The boot path asks the plan before it trusts a stored deadline. */
    @Test
    fun p4_23_the_boot_path_asks_the_plan_before_re_arming() {
        val body = bootBody()
        assertTrue(
            body.contains("allowsCountdown"),
            "the boot re-arm trusts a stored deadline on the switch alone, so every reboot of a " +
                "lapsed phone fires another paid cycle and re-arms its alarms",
        )
    }

    /** The snooze notice asks the plan too, so it stops inviting the owner to a feature he lost. */
    @Test
    fun p4_23_the_snooze_notice_asks_the_plan() {
        val body = snoozeBody()
        assertTrue(
            body.contains("allowsCountdown"),
            "the snooze notice is posted for a plan that no longer has a countdown to snooze",
        )
    }
}
