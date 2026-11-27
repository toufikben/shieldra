package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-30: the poller keeps polling the bot credentials it read once, so a corrected token or chat id
 * never takes effect while it runs.
 *
 * `loop()` reads `DeliverySettingsStore` a single time, builds one `TelegramCommandClient` from the bot
 * it finds there, takes that delivery's chat id as the only owner, and then polls for the life of the
 * thread with that client and that chat id. Nothing re-reads those settings.
 *
 * Saving the Alert channels does not help either: `syncRemoteControl` calls `start()`, and `start()`
 * returns because the thread is still alive (P4-28 made it honest about that, which is exactly why this
 * now matters). So an owner who arms protection, then discovers the bot token was mistyped and fixes
 * it, has a running poller connected to a bot that was never his — and one that ignores his own chat,
 * because the chat id is the old one too. The commands silently stop answering and every screen says
 * the setup is saved.
 *
 * The same class as P4-20/P4-21/P4-23/P4-24/P4-25/P4-27: a decision read once at the start and never
 * asked again while it still has an effect.
 */
class RemoteCredentialFreshnessTruthTest {

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

    /** The `loop` body, where the credentials are read and then used for the whole run. */
    private fun loopBody(): String {
        val start = poller.indexOf("private fun loop()")
        assertTrue(start > 0, "the poll loop is gone, so this finding must be re-measured")
        val end = poller.indexOf("\n    private fun execute(", start)
        return poller.substring(start, if (end > start) end else poller.length)
    }

    /** The premise: saving the channels is offered as the fix, and it does reach the poller. */
    @Test
    fun p4_30_saving_the_channels_still_reaches_the_poller() {
        val start = nav.indexOf("AlertChannelsScreen(")
        assertTrue(start > 0, "the alert-channels screen is gone, so this finding must be re-measured")
        val end = nav.indexOf("onBack = { navController.popBackStack() },", start)
        val body = nav.substring(start, if (end > start) end else nav.length)
        assertTrue(
            body.contains("syncRemoteControl"),
            "saving the channels no longer reaches the poller, so this finding must be re-measured",
        )
    }

    /** And a running poller refuses a second start, which is why the loop itself must re-read. */
    @Test
    fun p4_30_a_running_poller_refuses_a_restart() {
        val start = poller.indexOf("fun start()")
        assertTrue(start > 0, "the poller's start is gone, so this finding must be re-measured")
        val body = poller.substring(start, poller.indexOf("\n    @Synchronized", start))
        assertTrue(
            body.contains("isAlive"),
            "start() is no longer guarded by liveness, so this finding must be re-measured",
        )
    }

    /** So the loop asks for the credentials again while it runs. */
    @Test
    fun p4_30_the_loop_re_reads_the_bot_credentials() {
        val body = loopBody()
        val loopStart = body.indexOf("while (!Thread.currentThread().isInterrupted")
        assertTrue(loopStart > 0, "the poll loop's condition is gone, so this finding must be re-measured")
        val inside = body.substring(loopStart)
        assertTrue(
            Regex("""DeliverySettingsStore\(app\)\.load\(\)""").containsMatchIn(inside),
            "the poll loop reads the bot credentials once, before it starts, and polls with them for " +
                "the rest of the thread's life, so a mistyped token or chat id that the owner corrects " +
                "is never picked up while the poller runs — the commands simply stop answering",
        )
    }
}
