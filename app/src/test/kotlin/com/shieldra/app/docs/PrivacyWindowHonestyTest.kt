package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The privacy screen states the retention windows as a promise, in the same breath as what the app
 * deletes. `privacy_control_body` read "...delete it automatically after 90 days (events) and 30 days
 * (evidence)" — the shipped pair, hard-coded, inside a sentence the owner reads as the app's own account
 * of what it will do with their data.
 *
 * Since P1-2 the sweeper runs on the owner's `retentionDays` setting, so that sentence is false for every
 * owner who changed it, in exactly the place where being wrong matters most. A privacy statement is not
 * a marketing line: it is what the owner relies on when deciding whether to leave the app installed.
 *
 * The other four paragraphs on that screen describe fixed behaviour and stay fixed. This one carries
 * numbers, so it has to carry them as arguments and be formatted from the policy in force.
 */
class PrivacyWindowHonestyTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun text(relative: String): String = File(root, "app/src/main/$relative").readText()

    @Test
    fun p1_2_the_privacy_statement_takes_its_windows_as_arguments() {
        for (locale in listOf("values", "values-ar")) {
            val body = Regex(
                "<string name=\"privacy_control_body\"[^>]*>(.*?)</string>",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text("res/$locale/strings.xml"))?.groupValues?.get(1)
            assertTrue(body != null, "$locale has no privacy_control_body")
            assertFalse(
                Regex("\\b90\\b|\\b30\\b").containsMatchIn(body!!),
                "$locale: the privacy statement still hard-codes the shipped windows: $body",
            )
            assertTrue(
                body.contains("%1\$d") && body.contains("%2\$d"),
                "$locale: the privacy statement does not take the two windows as arguments: $body",
            )
        }
    }

    @Test
    fun p1_2_the_privacy_screen_formats_that_one_line_from_the_policy() {
        val screen = text("kotlin/com/shieldra/app/presentation/screen/settings/SettingsInfoScreens.kt")
        val start = screen.indexOf("fun PrivacyScreen(")
        assertTrue(start > 0, "PrivacyScreen is gone")
        val body = screen.substring(start).let {
            val next = it.indexOf("\n@Composable", 1)
            if (next > 0) it.substring(0, next) else it
        }
        assertTrue(
            body.contains("privacy_control_body"),
            "the privacy screen stopped showing the statement at all",
        )
        assertTrue(
            body.contains("policy.eventMetadataRetentionDays") &&
                body.contains("policy.evidenceRetentionDays"),
            "the privacy screen prints the windows from something other than the policy in force",
        )
        assertTrue(
            body.contains("policy: EvidenceRetentionPolicy"),
            "PrivacyScreen is given no policy, so it cannot state the real windows",
        )
    }
}
