package com.shieldra.app.docs

import com.shieldra.app.navigation.IntroCards
import com.shieldra.app.navigation.OnboardingState
import com.shieldra.app.navigation.ShieldraRoutes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * P4-22 (owner order 2026-11-26): short explanation cards at the start of the app, one per feature,
 * after setup and never before it.
 *
 * Two things are being pinned here, and they are different in kind:
 *
 * 1. **The flow.** The cards come after the owner has finished setup, one tap skips them, and a
 *    completed set is recorded so a phone that already holds real evidence is not shown the tour again
 *    on every cold start. That is the same lesson A-27 taught about onboarding itself.
 * 2. **The claims.** A card that describes the app is a promise printed on the screen the owner opens
 *    first, so every number in it is read out of the code that enforces it: the siren's two-minute cap,
 *    the wipe's ten-minute window, the thirty-day evidence retention, the two-attempt lock threshold.
 *    A card that says "rings until you stop it" when the siren stops by itself is the class of defect
 *    the audit rounds keep finding.
 */
class IntroCardsTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String =
        File(root, "app/src/main/kotlin/$relative").let { if (it.isFile) it.readText() else "" }

    private fun resource(relative: String): String =
        File(root, "app/src/main/res/$relative/strings.xml").let { if (it.isFile) it.readText() else "" }

    private fun stringValue(name: String, relative: String): String? {
        val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(resource(relative))
        return match?.groupValues?.get(1)
    }

    private val introScreen: String by lazy { source("com/shieldra/app/presentation/screen/onboarding/IntroScreen.kt") }
    private val navHost: String by lazy { source("com/shieldra/app/navigation/ShieldraNavHost.kt") }
    private val mainActivity: String by lazy { source("com/shieldra/app/MainActivity.kt") }

    @Test
    fun p4_22_every_named_feature_has_a_card() {
        val keys = IntroCards.cards.map { it.key }
        assertEquals(
            listOf(
                "lock", "motion", "sim", "battery", "snatch", "safe_zones",
                "evidence", "delivery", "remote", "siren", "tracking", "wipe",
            ),
            keys,
            "the tour must cover every feature the owner named, in the order he named them",
        )
    }

    @Test
    fun p4_22_each_card_has_a_title_and_a_body_in_both_locales() {
        for (card in IntroCards.cards) {
            for (locale in listOf("values", "values-ar")) {
                for (suffix in listOf("title", "body")) {
                    val name = "intro_${card.key}_$suffix"
                    val value = stringValue(name, locale)
                    assertTrue(
                        !value.isNullOrBlank(),
                        "$locale has no $name, so the card renders a blank line for an owner reading that language",
                    )
                }
            }
        }
    }

    @Test
    fun p4_22_the_screen_references_the_resources_the_cards_name() {
        // The card list holds resource ids, so a key and its strings can drift apart without a compile
        // error. This is the join: every key the model names must be the resource the screen reads.
        for (card in IntroCards.cards) {
            for (suffix in listOf("title", "body")) {
                val name = "intro_${card.key}_$suffix"
                assertTrue(
                    introScreen.contains("R.string.$name"),
                    "IntroScreen does not read $name, so that card's $suffix is never shown",
                )
            }
        }
    }

    @Test
    fun p4_22_the_cards_come_after_setup_and_never_before_it() {
        assertEquals(
            ShieldraRoutes.ONBOARDING_WELCOME,
            OnboardingState.startDestination(onboardingCompleted = false, introCardsSeen = false),
            "the tour must not appear in front of a phone that has not been set up",
        )
        assertEquals(
            ShieldraRoutes.ONBOARDING_INTRO,
            OnboardingState.startDestination(onboardingCompleted = true, introCardsSeen = false),
            "a finished setup that has not seen the tour must open the tour",
        )
        assertEquals(
            ShieldraRoutes.DASHBOARD,
            OnboardingState.startDestination(onboardingCompleted = true, introCardsSeen = true),
            "a seen tour must not come back",
        )
    }

    @Test
    fun p4_22_the_setup_screen_leads_to_the_cards_not_to_the_dashboard() {
        val protection = navHost.indexOf("composable(ShieldraRoutes.ONBOARDING_PROTECTION)")
        assertTrue(protection > 0, "the setup step is gone, so this finding must be re-measured")
        val next = navHost.indexOf("composable(ShieldraRoutes.ONBOARDING_INTRO)")
        assertTrue(next > protection, "the tour is not a step after setup")
        val step = navHost.substring(protection, if (next > 0) next else navHost.length)
        assertTrue(
            step.contains("ShieldraRoutes.ONBOARDING_INTRO"),
            "finishing setup still jumps to the dashboard, so the owner never sees the tour",
        )
    }

    @Test
    fun p4_22_one_tap_skips_the_tour() {
        // The tour has exactly one exit — the same one the last card uses — and it is offered as a
        // labelled control from the first card on. Asserting on the label and its variant rather than on
        // a parameter name keeps the claim about what the owner sees: a Skip button beside Next.
        assertTrue(
            introScreen.contains("R.string.action_skip"),
            "the tour offers no skip, so an owner who already knows the app must page through it to get in",
        )
        assertTrue(
            Regex("""text\s*=\s*stringResource\(R\.string\.action_skip\)[\s\S]{0,200}?ShieldraButtonVariant\.Ghost""")
                .containsMatchIn(introScreen),
            "the skip is not a ghost button beside the primary action, so it competes with Next instead of " +
                "standing beside it",
        )
    }

    @Test
    fun p4_22_finishing_the_tour_records_it() {
        assertTrue(
            mainActivity.contains("IntroCardsRecord"),
            "the tour's completion is not recorded, so every cold start shows it again on a phone that " +
                "already holds real evidence",
        )
        assertTrue(
            navHost.contains("onIntroCardsSeen"),
            "the navigation host never reports a finished tour, so nothing can record it",
        )
    }

    @Test
    fun p4_22_the_siren_card_names_its_cap() {
        for ((locale, marker) in listOf("values" to "2 min", "values-ar" to "دقيقت")) {
            val body = stringValue("intro_siren_body", locale).orEmpty()
            assertTrue(
                body.contains(marker),
                "$locale: the siren card does not name the two-minute cap the code enforces, so it " +
                    "promises a siren that never stops: $body",
            )
        }
    }

    @Test
    fun p4_22_the_wipe_card_names_its_window() {
        for ((locale, marker) in listOf("values" to "10 min", "values-ar" to "عشر دقائق")) {
            val body = stringValue("intro_wipe_body", locale).orEmpty()
            assertTrue(
                body.contains(marker),
                "$locale: the wipe card does not name the ten-minute confirmation window: $body",
            )
        }
    }

    @Test
    fun p4_22_the_evidence_card_names_the_retention() {
        for ((locale, marker) in listOf("values" to "30 days", "values-ar" to "30 يوماً")) {
            val body = stringValue("intro_evidence_body", locale).orEmpty()
            assertTrue(
                body.contains(marker),
                "$locale: the evidence card does not name the default retention: $body",
            )
        }
    }

    @Test
    fun p4_22_the_lock_card_names_the_threshold() {
        for ((locale, marker) in listOf("values" to "2 attempts", "values-ar" to "مرتين")) {
            val body = stringValue("intro_lock_body", locale).orEmpty()
            assertTrue(
                body.contains(marker),
                "$locale: the lock card does not name the failed-attempt threshold the code enforces: $body",
            )
        }
    }

    @Test
    fun p4_22_the_remote_card_names_the_commands_that_exist() {
        val body = stringValue("intro_remote_body", "values").orEmpty()
        val poller = source("com/shieldra/protection/RemoteCommandPoller.kt")
        for (command in listOf("/locate", "/status", "/photo", "/alarm")) {
            assertTrue(body.contains(command), "the remote card omits $command")
            assertTrue(poller.contains("\"$command\""), "the remote card promises $command, which no command handles")
        }
    }

    @Test
    fun p4_22_the_tour_is_not_part_of_the_onboarding_flow_it_follows() {
        // A finished setup may never restart onboarding, and the tour must not be mistaken for a step
        // that has to be completed to reach the app.
        assertNotEquals(
            ShieldraRoutes.ONBOARDING_WELCOME,
            OnboardingState.startDestination(onboardingCompleted = true, introCardsSeen = false),
        )
    }
}
