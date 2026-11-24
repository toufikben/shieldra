package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The retention screen must report the windows the sweeper actually uses.
 *
 * `RetentionScreen` printed `EvidenceRetentionPolicy.EVIDENCE_RETENTION_DAYS` and
 * `EVENT_METADATA_RETENTION_DAYS` — the compiled-in 30 and 90. Since P1-2 the sweeper is built from
 * the owner's `retentionDays` setting instead (`EvidenceRetentionPolicy.fromOwnerDays`), so an owner
 * who chose 14 days was told "Event records kept for 90 days / Evidence kept for 30 days" on the very
 * screen where they check what this phone holds, while the sweep was really expiring at 14 and 42.
 *
 * A screen that states a retention window is making a promise about deletion, which is the one promise
 * in this app that cannot be walked back. It therefore has to read the policy, not the constants.
 */
class RetentionWindowHonestyTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    @Test
    fun p1_2_the_retention_screen_prints_the_policy_it_was_given() {
        val screen = source("com/shieldra/app/presentation/screen/settings/SettingsInfoScreens.kt")
        val start = screen.indexOf("fun RetentionScreen(")
        assertTrue(start > 0, "RetentionScreen is gone")
        // The function body, up to the next top-level declaration.
        val body = screen.substring(start).let { it.substring(0, it.indexOf("\n@Composable", 1).takeIf { i -> i > 0 } ?: it.length) }
        assertFalse(
            body.contains("EVIDENCE_RETENTION_DAYS"),
            "RetentionScreen still prints the compiled-in evidence window instead of the owner's",
        )
        assertFalse(
            body.contains("EVENT_METADATA_RETENTION_DAYS"),
            "RetentionScreen still prints the compiled-in event window instead of the owner's",
        )
        assertTrue(
            body.contains("policy.evidenceRetentionDays") && body.contains("policy.eventMetadataRetentionDays"),
            "RetentionScreen does not read both windows off the policy it is handed",
        )
    }

    @Test
    fun p1_2_the_host_hands_it_the_same_policy_the_sweeper_uses() {
        val host = source("com/shieldra/app/navigation/ShieldraNavHost.kt")
        val start = host.indexOf("ShieldraRoutes.RETENTION_INFO")
        assertTrue(start > 0, "the retention route is gone")
        val body = host.substring(start, (start + 1400).coerceAtMost(host.length))
        assertTrue(
            body.contains("EvidenceRetentionPolicy.fromOwnerDays"),
            "the host builds no policy for the screen, so the screen cannot know the real windows",
        )
        assertTrue(
            body.contains("retentionDays"),
            "the host does not read the owner's retention setting for the screen",
        )
    }
}
