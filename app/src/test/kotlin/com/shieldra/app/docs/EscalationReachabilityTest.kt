package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The escalation the policy says the owner sets is a switch no screen draws.
 *
 * `EscalationPolicy` opens with "the owner sets how many [intrusions] within the window do", and the
 * service honours that answer in two places: `maybeEscalate` counts confirmed intrusions inside
 * `WINDOW_MS` and sounds the siren at `escalationThreshold`, and the composite-tamper branch skips
 * straight to the siren when `escalationEnabled` is on. `ProtectionChange.weakens` even treats
 * switching it off, or raising the threshold, as a live weakening the owner would notice.
 *
 * None of that is reachable. `escalationEnabled` defaults to `false`, no screen renders a
 * `GuardToggle` for it, `ProtectionSetupScreen` never names either field, no `ProtectionRuntime`
 * writer exists beside `setMotionEnabled`/`setGeofenceEnabled`, and no `protection_escalation*`
 * string exists in either locale — so the switch cannot be turned on, the threshold cannot be
 * chosen, and the feature is dead code with a doc comment claiming the owner controls it.
 *
 * The test pins the whole chain: the policy's bounds, the service's use of them, a control on the
 * screen bound to both fields at those bounds, and the copy in both languages.
 */
class EscalationReachabilityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun locale(name: String): String =
        File(root, "app/src/main/res/$name/strings.xml").readText()

    /**
     * The premise. If the service stops consulting these fields the whole feature is gone and this
     * test is measuring nothing; the numbers are here so the copy below cannot drift from them.
     */
    @Test
    fun p4_11_the_service_really_runs_an_escalation_on_owner_settings() {
        val service = main("kotlin/com/shieldra/protection/ProtectionService.kt")
        assertTrue(
            service.contains("if (!settings.escalationEnabled) return"),
            "maybeEscalate no longer consults escalationEnabled; the switch this test demands would gate nothing",
        )
        assertTrue(
            service.contains("EscalationPolicy.shouldEscalate(intrusionTimes.toList(), now, settings.escalationThreshold)"),
            "the service no longer passes the owner's threshold to the policy, so the threshold would be decorative",
        )

        val policy = main("kotlin/com/shieldra/protection/EscalationPolicy.kt")
        assertTrue(
            policy.contains("WINDOW_MS = 10 * 60_000L"),
            "the window moved off ten minutes; the subtitle in both languages says ten and would now lie",
        )
        assertTrue(
            service.contains("ESCALATION_SIREN_MAX_MS = 60_000L"),
            "the escalation siren cap moved off one minute; the subtitle says one minute and would now lie",
        )
    }

    /** The one place the owner could set it, bound to both fields at the policy's own bounds. */
    @Test
    fun p4_11_the_screen_offers_the_switch_and_the_threshold() {
        val screen = main(
            "kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt",
        )
        assertTrue(
            screen.contains("draft.escalationEnabled"),
            "no control on the protection screen is bound to escalationEnabled, so the default false is permanent",
        )
        assertTrue(
            screen.contains("draft.copy(escalationEnabled = "),
            "nothing writes escalationEnabled back into the draft, so a drawn switch would still save nothing",
        )
        assertTrue(
            screen.contains("draft.escalationThreshold"),
            "the owner cannot see or choose how many intrusions escalate, which is the number the policy doc says he sets",
        )
        assertTrue(
            screen.contains("draft.copy(escalationThreshold = "),
            "nothing writes escalationThreshold back into the draft",
        )
        assertTrue(
            screen.contains("EscalationPolicy.MIN_THRESHOLD") &&
                screen.contains("EscalationPolicy.MAX_THRESHOLD"),
            "the stepper does not take its range from EscalationPolicy, so it can pick a value the store clamps " +
                "and the owner would see a number that is not the one in force",
        )
    }

    /**
     * Both languages name the control and the threshold, with the threshold's number as a format
     * argument rather than baked text: the locale gate compares format arguments key by key, and a
     * literal in one language is a row that cannot be translated.
     */
    @Test
    fun p4_11_both_languages_name_the_escalation() {
        for (name in listOf("values", "values-ar")) {
            val text = locale(name)
            for (key in listOf(
                "protection_escalation",
                "protection_escalation_desc",
                "protection_escalation_threshold",
            )) {
                assertTrue(
                    text.contains("name=\"$key\""),
                    "$name has no $key, so the escalation is unreachable in that language",
                )
            }
            assertTrue(
                Regex("""name="protection_escalation_threshold">[^<]*%1${'$'}d""").containsMatchIn(text),
                "$name's protection_escalation_threshold carries no %1\$d, so the chosen number cannot be shown",
            )
        }
    }
}
