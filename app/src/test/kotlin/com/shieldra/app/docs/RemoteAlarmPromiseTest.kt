package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two places tell the owner that `/alarm` sounds the siren "until you send /stopalarm":
 * `panic_step_signal_body` and, in shorter form, the remote-command help text. That is not what the
 * code does, and the code is right to do what it does.
 *
 * `RemoteCommandPoller.REMOTE_ALARM_MAX_MS` is 120 000 ms, and the comment beside it says why: a leaked
 * bot token must not be able to loop the siren until the battery dies. `Siren.start(app, maxMs)` stops
 * on its own at that cap. So an owner who reads the panic guide and walks away expecting the alarm to
 * keep sounding gets two minutes and then silence — the opposite surprise from the one the sentence
 * prepares them for, on the screen they open while deciding how to respond to an intrusion.
 *
 * The promise has to name the cap. It is the kind of number the owner can plan around ("it will ring
 * for two minutes, send it again") and the kind that cannot drift: the test reads the constant.
 */
class RemoteAlarmPromiseTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    private fun stringValue(name: String, relative: String): String {
        val text = File(root, "app/src/main/res/$relative/strings.xml").readText()
        val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(text)
        assertTrue(match != null, "$relative has no $name")
        return match!!.groupValues[1]
    }

    /** The cap is real and the siren really stops at it. */
    @Test
    fun p4_8_the_remote_alarm_stops_by_itself() {
        val poller = source("com/shieldra/protection/RemoteCommandPoller.kt")
        assertTrue(
            poller.contains("REMOTE_ALARM_MAX_MS"),
            "the remote alarm lost its cap, so a leaked token could ring the phone until the battery dies",
        )
        assertTrue(
            poller.contains("Siren.start(app, REMOTE_ALARM_MAX_MS)"),
            "the remote alarm no longer hands its cap to the siren",
        )
    }

    @Test
    fun p4_8_the_panic_guide_names_the_cap() {
        for ((locale, marker) in listOf("values" to "2 min", "values-ar" to "دقيقت")) {
            val line = stringValue("panic_step_signal_body", locale)
            assertTrue(
                line.contains(marker),
                "$locale: panic_step_signal_body says the siren rings until /stopalarm, which the 2-minute cap makes false: $line",
            )
        }
    }

    @Test
    fun p4_8_the_remote_help_names_the_cap() {
        for ((locale, marker) in listOf("values" to "2 min", "values-ar" to "دقيقت")) {
            val line = stringValue("protection_remote_help", locale)
            assertTrue(
                line.contains(marker),
                "$locale: protection_remote_help describes /alarm without its cap: $line",
            )
        }
    }
}
