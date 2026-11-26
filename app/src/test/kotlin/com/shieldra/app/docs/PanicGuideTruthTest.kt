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
 * Design B (2026-11-06) had removed the failed-unlock guard, and this test then forbade the guide
 * from naming one. A-46 (owner decision 2026-11-10) restored it: the device-admin role is declared
 * with `watch-login` in `res/xml/device_admin.xml` and `LockGuardReceiver` turns a failed unlock past
 * the owner's threshold into a `LOCK_CONFIRMED_EVENT`. So the direction of the claim is now the
 * other one — the guide may name failed unlocks **only while** that sensor really exists, and the
 * sensor is pinned here rather than assumed.
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

    /** The failed-unlock sensor the guide leans on is a real, declared, wired sensor. */
    @Test
    fun p4_9_the_lock_guard_the_guide_names_really_fires() {
        val adminXml = main("res/xml/device_admin.xml")
        assertTrue(
            adminXml.contains("<watch-login"),
            "the guide names failed unlocks, but the device-admin policy no longer asks for watch-login",
        )
        val manifest = main("AndroidManifest.xml")
        assertTrue(
            manifest.contains("com.shieldra.protection.LockGuardReceiver"),
            "the guide names failed unlocks, but no receiver is registered to hear them",
        )
        assertTrue(
            main("kotlin/com/shieldra/domain/SecurityEventContracts.kt").contains("LOCK_CONFIRMED_EVENT"),
            "the guide names failed unlocks, but the build records no lock event to show for them",
        )
        assertTrue(
            main("kotlin/com/shieldra/protection/LockAlertPolicy.kt").isNotBlank(),
            "the guide names failed unlocks, but the rationing rule that sends them is gone",
        )
    }

    /** The guide's list of guards is drawn from the guards the build really has. */
    @Test
    fun p4_9_the_guide_names_only_guards_that_can_fire() {
        val contracts = main("kotlin/com/shieldra/domain/GuardContracts.kt")
        for (guard in listOf("PANIC", "SIM", "MOTION")) {
            assertTrue(contracts.contains(guard), "GuardKind lost $guard, so the guide's list is out of date")
        }
        // A wrong unlock is not a GuardKind category: it arrives through the device-admin receiver,
        // which is exactly why this test pins the declaration above instead of the enum.
        assertFalse(
            contracts.contains(Regex("enum class GuardKind \\{[^}]*\\bLOCK\\b")),
            "a LOCK GuardKind category appeared; a failed unlock is a device-admin callback, not a boundary",
        )
    }

    /** And the sentence that names them says it in both locales, without naming a guard that is gone. */
    @Test
    fun p4_9_the_guide_still_names_the_guards_that_do_fire() {
        for ((locale, phrase) in listOf(
            "values" to "failed unlock",
            "values-ar" to "محاولات فتح فاشلة",
        )) {
            val line = stringValue("panic_step_photo_body", locale)
            assertTrue(
                line.contains(phrase, ignoreCase = true),
                "$locale: panic_step_photo_body no longer tells the owner a failed unlock may already " +
                    "have sent a photo, though the Lock guard is back: $line",
            )
        }
        assertFalse(stringValue("panic_step_photo_body", "values").isBlank(), "the guide's photo step is empty")
        assertFalse(stringValue("panic_step_photo_body", "values-ar").isBlank(), "the guide's photo step is empty")
    }
}
