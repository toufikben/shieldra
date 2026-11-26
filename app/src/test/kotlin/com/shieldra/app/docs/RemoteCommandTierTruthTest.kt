package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-20: the remote-command poller outlives the plan that allowed it.
 *
 * `ProtectionService.onStartCommand` asks the plan before it starts the poller
 * (`remoteCommandsEnabled && TierGate.allowsRemote(tier)`), so a Free owner never begins a poll
 * loop. But the loop's own condition is `active()`, and `active()` read only two things — armed and
 * the owner's switch — never the plan. A tier is not fixed for the life of a process: `PlayProStore`
 * rewrites it whenever Google Play answers (`EntitlementPolicy.resolve`), and nothing restarts the
 * service when it does. So a phone that was Premium when protection started, and is Free by the time
 * the subscription lapses, keeps long-polling the bot and keeps obeying `/photo`, `/alarm` and
 * `/track` — a paid feature still running on a plan that does not include it.
 *
 * This is the same defect the map row was fixed for (P4-7): a stored switch reported as a running
 * feature without asking the plan. The difference is that the map row only *said* it; this one acts.
 *
 * The fix is one clause on the loop's own condition, so the check happens every time round rather
 * than once at start.
 */
class RemoteCommandTierTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    private val poller: String by lazy { source("com/shieldra/protection/RemoteCommandPoller.kt") }

    /** The body of `active()`, which is the loop's own condition. */
    private fun activeBody(): String {
        val start = poller.indexOf("private fun active(")
        assertTrue(start > 0, "the poller no longer has an active() condition, so this finding must be re-measured")
        return poller.substring(start, poller.indexOf("\n\n", start).takeIf { it > start } ?: poller.length)
    }

    /** The loop is driven by `active()`, so that condition is what ends a lapsed subscription. */
    @Test
    fun p4_20_the_loop_is_driven_by_the_active_condition() {
        assertTrue(
            poller.contains("while (!Thread.currentThread().isInterrupted && active())"),
            "the poll loop no longer asks active(), so a clause added there would not stop it",
        )
    }

    /** `active()` asks the plan, so a downgrade ends the poll instead of waiting for a restart. */
    @Test
    fun p4_20_the_active_condition_asks_the_plan() {
        val body = activeBody()
        assertTrue(
            body.contains("allowsRemote"),
            "active() does not ask the plan, so a Free phone keeps long-polling the bot and keeps " +
                "obeying /photo, /alarm and /track after the subscription lapses",
        )
    }

    /** And it asks the plan about the tier it read, not about a constant. */
    @Test
    fun p4_20_the_plan_question_uses_the_stored_tier() {
        val body = activeBody()
        assertTrue(
            body.contains(".tier"),
            "active() asks the plan without reading the stored tier, so the question has no answer to use",
        )
    }

    /** The start path still gates, so the two ends agree rather than one carrying the whole rule. */
    @Test
    fun p4_20_the_service_still_gates_the_start() {
        val service = source("com/shieldra/protection/ProtectionService.kt")
        assertTrue(
            service.contains("s.remoteCommandsEnabled && com.shieldra.billing.TierGate.allowsRemote(s.tier)"),
            "the service no longer gates the start on the plan, so this finding must be re-measured",
        )
    }
}
