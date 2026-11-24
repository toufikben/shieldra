package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The retention screen must report the windows the sweeper is actually running with.
 *
 * `RetentionScreen` printed `EvidenceRetentionPolicy.EVIDENCE_RETENTION_DAYS` and
 * `EVENT_METADATA_RETENTION_DAYS` — the compiled-in 30 and 90. Since P1-2 the sweeper is built from
 * the owner's `retentionDays` setting instead (`EvidenceRetentionPolicy.fromOwnerDays`), so an owner
 * who chose 14 days was told "Event records kept for 90 days / Evidence kept for 30 days" on the very
 * screen where they check what this phone holds, while the sweep was really expiring at 14 and 42.
 *
 * Reading the live setting is not enough either, and that is the sharper half of this test. The policy
 * is fixed when storage opens: `ShieldraApp` says so in as many words — a changed setting applies to
 * the next process, never as a half-applied window inside the running one. A screen that computed the
 * policy from the current setting would therefore promise the new window while the sweeper in this very
 * process still holds the old one. The screen has to be handed the policy the sweeper was built with.
 *
 * A screen that states a retention window is making a promise about deletion, which is the one promise
 * in this app that cannot be walked back.
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
        val body = screen.substring(start).let {
            val next = it.indexOf("\n@Composable", 1)
            if (next > 0) it.substring(0, next) else it
        }
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
    fun p1_2_the_host_hands_it_the_policy_the_sweeper_runs_with() {
        val host = source("com/shieldra/app/navigation/ShieldraNavHost.kt")
        val start = host.indexOf("ShieldraRoutes.RETENTION_INFO")
        assertTrue(start > 0, "the retention route is gone")
        val body = host.substring(start, (start + 1600).coerceAtMost(host.length))
        assertTrue(
            body.contains("retentionPolicy"),
            "the host hands the screen no policy, so the screen cannot know the real windows",
        )
        // The policy has to come from the open storage, which is the object holding the sweeper. A
        // policy recomputed from the live setting here would promise a window this process does not use.
        assertTrue(
            body.contains("installedStorage?.retentionPolicy"),
            "the retention screen is not given the open storage's policy",
        )
        val storage = source("com/shieldra/storage/LocalStorageFactory.kt")
        assertTrue(
            storage.contains("val retentionPolicy"),
            "LocalShieldraStorage does not expose the policy its sweeper runs with",
        )
        assertFalse(
            body.contains("fromOwnerDays"),
            "the host recomputes the policy from the live setting instead of using the running one",
        )
    }

    @Test
    fun p1_2_the_policy_in_force_is_the_owner_setting_applied_at_open() {
        val storage = source("com/shieldra/storage/LocalStorageFactory.kt")
        assertTrue(
            storage.contains("EvidenceRetentionPolicy.fromOwnerDays(retentionDays())"),
            "the sweeper is no longer built from the owner's retention setting at open time",
        )
        val sweeper = source("com/shieldra/storage/EvidenceRetentionSweeper.kt")
        assertTrue(
            sweeper.contains("val policy: EvidenceRetentionPolicy"),
            "the sweeper hides the policy it runs with, so nothing can report it truthfully",
        )
    }

    /**
     * The Settings row is the *other* place the windows are stated, and it carries the same claim in one
     * line: `settings_retention_windows` reads "Applied: 90-day events, 30-day evidence". A hard-coded
     * sentence cannot be right for an owner who chose any other number, so the row has to take its
     * figures from the policy too.
     */
    @Test
    fun p1_2_the_settings_row_takes_its_figures_from_the_policy() {
        val en = File(root, "app/src/main/res/values/strings.xml").readText()
        val ar = File(root, "app/src/main/res/values-ar/strings.xml").readText()
        for ((name, text) in listOf("values" to en, "values-ar" to ar)) {
            val line = Regex("<string name=\"settings_retention_windows\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1)
            assertTrue(line != null, "$name has no settings_retention_windows")
            assertTrue(
                line!!.contains("%1\$d") && line.contains("%2\$d"),
                "$name: settings_retention_windows hard-codes its figures instead of taking them: $line",
            )
        }
        val rows = source("com/shieldra/app/presentation/settings/HonestSettingsRows.kt")
        assertTrue(
            rows.contains("retentionPolicy"),
            "honestSettingsRows is not given the policy, so the retention row cannot state real figures",
        )
        assertTrue(
            rows.contains("R.string.settings_retention_windows,"),
            "the retention row still prints the static sentence",
        )
    }
}
