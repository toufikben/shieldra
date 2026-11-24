package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The panic guide is read on the worst day the owner will ever have with this app, from a borrowed
 * laptop, while deciding whether to keep waiting for an alert or to give up and wipe. Every guard it
 * names in that moment has to be a guard this build can actually fire.
 *
 * It names one that cannot. `panic_step_photo_body` says an alert that already fired may have been
 * "panic, failed unlocks, a SIM change, motion". There is no failed-unlock guard and there has not
 * been one since Design B (2026-11-06): its only sensor was the device-admin `onPasswordFailed`
 * callback, an authority the app does not hold, so `GuardBoundaries` deleted the `LockGuard` and
 * `GuardContracts` deleted the `LOCK` category. A wrong PIN therefore raises nothing, and an owner
 * who reads the broken screen of a phone someone is guessing at and waits for that alert waits for
 * something this app has never sent.
 *
 * The direction matters. The sentence is not over-claiming a habit, it is over-claiming evidence:
 * it tells the owner a photograph of the person pressing at the keypad may already be in the chat,
 * which is exactly the artifact they would look for before deciding the phone is worth chasing.
 */
class PanicGuideTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun stringValue(name: String, locale: String): String {
        val text = File(root, "app/src/main/res/$locale/strings.xml").readText()
        val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(text)
        assertTrue(match != null, "$locale has no $name")
        return match!!.groupValues[1]
    }

    /** The guide's list of guards is drawn from the guards the build really has. */
    @Test
    fun p4_9_the_guide_names_only_guards_that_can_fire() {
        val contracts = main("kotlin/com/shieldra/domain/GuardContracts.kt")
        val eventTypes = main("kotlin/com/shieldra/domain/SecurityEventContracts.kt")
        // The premise of the claim, stated once: no lock guard, no lock event type.
        assertFalse(
            contracts.contains(Regex("enum class GuardKind \\{[^}]*\\bLOCK\\b")),
            "a LOCK guard category is back; the guide may name failed unlocks again only with a real sensor",
        )
        assertFalse(
            eventTypes.contains("LOCK"),
            "a LOCK event type is back; the guide may name failed unlocks again only with a real sensor",
        )
        assertFalse(
            main("kotlin/com/shieldra/detection/GuardBoundaries.kt").contains("interface LockGuard"),
            "a LockGuard boundary is back; the guide may name failed unlocks again only with a real sensor",
        )
        assertFalse(
            main("kotlin/com/shieldra/domain/Signal.kt").contains("LOCK,"),
            "SignalType still carries a LOCK value with no guard to feed it",
        )

        for ((locale, phrase) in listOf(
            "values" to "failed unlock",
            "values-ar" to "محاولات فتح",
        )) {
            val line = stringValue("panic_step_photo_body", locale)
            assertFalse(
                line.contains(phrase, ignoreCase = true),
                "$locale: panic_step_photo_body tells the owner a failed unlock may already have sent a " +
                    "photo, but this build has no lock guard to fire one: $line",
            )
        }
    }

    /** The guards it does name are the ones the guide can honestly name. */
    @Test
    fun p4_9_the_guide_still_names_the_guards_that_do_fire() {
        val contracts = main("kotlin/com/shieldra/domain/GuardContracts.kt")
        for (guard in listOf("PANIC", "SIM", "MOTION")) {
            assertTrue(contracts.contains(guard), "GuardKind lost $guard, so the guide's list is out of date")
        }
        assertFalse(stringValue("panic_step_photo_body", "values").isBlank(), "the guide's photo step is empty")
        assertFalse(stringValue("panic_step_photo_body", "values-ar").isBlank(), "the guide's photo step is empty")
    }
}
