package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-28: the remote poller believes it is running after its thread has ended, so it can never be
 * started again in this process.
 *
 * `start()` refuses to do anything while its field is set:
 *
 * ```
 * @Synchronized fun start() { if (worker != null) return; worker = Thread({ loop() }, ...).apply { start() } }
 * ```
 *
 * Nothing clears that field when `loop()` returns. And `loop()` returns in two ordinary cases: when
 * Telegram is not enabled or not complete (the owner has not finished the bot setup yet), and when
 * `active()` turns false (a disarm, the switch off, a lapsed plan). In both the field stays non-null
 * over a thread that no longer exists.
 *
 * The consequences are permanent for the life of the process. An owner who arms protection before
 * finishing the Telegram setup, then finishes it, gets no poller: nothing restarts the service, and
 * even a Save that does reach [ProtectionService.syncRemoteControl] calls `start()`, which returns on
 * the dead field. The same happens to a subscription that lapses and is bought again, and to a phone
 * where the owner turned the switch off and on.
 *
 * The second half is the same shape: the poller reads the delivery settings once, when its thread
 * starts, so completing the bot in Alert channels has to reach it. Nothing there does.
 */
class RemotePollerLivenessTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val poller: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/RemoteCommandPoller.kt").readText()
    }

    private val nav: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt").readText()
    }

    /** The `start` body, where the field decides whether a thread is made. */
    private fun startBody(): String {
        val start = poller.indexOf("fun start()")
        assertTrue(start > 0, "the poller's start is gone, so this finding must be re-measured")
        val end = poller.indexOf("\n    }", start)
        return poller.substring(start, if (end > start) end else poller.length)
    }

    /** The premise: the loop really can end on its own, which is what strands the field. */
    @Test
    fun p4_28_the_loop_can_return_without_a_stop_call() {
        val start = poller.indexOf("private fun loop()")
        assertTrue(start > 0, "the poll loop is gone, so this finding must be re-measured")
        val body = poller.substring(start, poller.indexOf("\n    private fun execute(", start))
        assertTrue(
            body.contains("if (!delivery.telegramEnabled || !delivery.telegram.isComplete) return"),
            "the loop no longer returns early on an unfinished Telegram setup, so this finding must " +
                "be re-measured",
        )
        assertTrue(
            body.contains("while (!Thread.currentThread().isInterrupted && active())"),
            "the loop no longer ends on its own condition, so this finding must be re-measured",
        )
    }

    /** So the field must not outlive the thread it names. */
    @Test
    fun p4_28_a_finished_thread_is_not_recorded_as_running() {
        val body = startBody()
        assertTrue(
            body.contains("isAlive"),
            "start() refuses on a field that a finished thread left set, so after an unfinished " +
                "Telegram setup, a lapsed plan or a switch flipped off and on, remote control can " +
                "never start again in this process",
        )
    }

    /** And a Telegram setup finished later reaches the poller that was waiting for it. */
    @Test
    fun p4_28_saving_the_channels_restarts_the_poller() {
        val start = nav.indexOf("AlertChannelsScreen(")
        assertTrue(start > 0, "the alert-channels screen is gone, so this finding must be re-measured")
        val end = nav.indexOf("onBack = { navController.popBackStack() },", start)
        val body = nav.substring(start, if (end > start) end else nav.length)
        assertTrue(
            body.contains("syncRemoteControl"),
            "saving the bot credentials never reaches the poller, so the poller keeps waiting for a " +
                "Telegram setup that has already been done",
        )
    }

    /** The location stream has the same shape: its thread also ends on its own. */
    @Test
    fun p4_28_a_finished_track_thread_is_not_recorded_as_running() {
        val start = poller.indexOf("private fun startTrack(")
        assertTrue(start > 0, "the location stream is gone, so this finding must be re-measured")
        val body = poller.substring(start, poller.indexOf("\n    @Synchronized\n    private fun stopTrack(", start))
        assertTrue(
            body.contains("isAlive"),
            "the location stream refuses on a field its own loop left set when the plan or the switch " +
                "ended it, so /track on can never work again in this process",
        )
    }
}
