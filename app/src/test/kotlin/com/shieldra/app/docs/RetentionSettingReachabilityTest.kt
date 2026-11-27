package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The deletion window the app promises the owner can choose is a field nothing writes.
 *
 * Three separate places tell the owner the retention window is a setting: the README says the windows
 * are "both following the owner's setting", the privacy screen says Shieldra deletes data
 * "automatically after %1$d days (events) and %2$d days (evidence)" as part of "Your control", and
 * `EvidenceRetentionPolicy.fromOwnerDays` exists precisely to turn that setting into the windows the
 * sweeper runs with — `ShieldraApp` even reads `ProtectionSettingsStore.load().retentionDays` at
 * storage-open time, and `ProtectionSettingsStore.save` persists it.
 *
 * Nothing ever writes it. No screen renders a control, no `copy(retentionDays = )` exists anywhere in
 * `app/src/main`, and no `protection_retention*` string is in either locale. So `retentionDays` is
 * permanently 30 and the windows are permanently 30/30/90: the words "your control" and "following
 * the owner's setting" describe a knob the owner cannot turn.
 *
 * The test pins the chain the promise needs: a control on the protection screen bound to the field, a
 * range that reaches the policy's own bounds, and the copy in both languages.
 */
class RetentionSettingReachabilityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun locale(name: String): String =
        File(root, "app/src/main/res/$name/strings.xml").readText()

    /** The premise: the setting really is what the sweeper is built from, so a knob is meaningful. */
    @Test
    fun p4_12_the_setting_really_decides_the_windows() {
        val app = main("kotlin/com/shieldra/app/ShieldraApp.kt")
        assertTrue(
            app.contains(".retentionDays"),
            "the app no longer reads retentionDays at storage-open time; the setting would gate nothing",
        )
        val policy = main("kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt")
        assertTrue(
            policy.contains("fun fromOwnerDays(days: Int)"),
            "fromOwnerDays is gone, so the owner-day setting no longer becomes a policy",
        )
        val store = main("kotlin/com/shieldra/protection/ProtectionSettings.kt")
        assertTrue(
            Regex("""KEY_RETENTION,\s*settings\.retentionDays""").containsMatchIn(store),
            "save() no longer persists retentionDays, so even a new control would be lost on the next read",
        )
    }

    /** The one place the owner could set it. */
    @Test
    fun p4_12_the_screen_offers_the_retention_window() {
        val screen = main(
            "kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt",
        )
        assertTrue(
            screen.contains("draft.retentionDays"),
            "no control on the protection screen is bound to retentionDays, so the owner cannot choose " +
                "the window the privacy screen says is his to control",
        )
        assertTrue(
            screen.contains("draft.copy(retentionDays = "),
            "nothing writes retentionDays back into the draft, so a drawn control would still save nothing",
        )
        assertTrue(
            screen.contains("EvidenceRetentionPolicy.MIN_OWNER_DAYS") &&
                screen.contains("EvidenceRetentionPolicy.MAX_OWNER_DAYS"),
            "the retention control does not take its range from the policy, so it can offer a window the " +
                "sweeper would silently refuse",
        )
    }

    /** Both languages name it, with the number as a format argument the locale gate can compare. */
    @Test
    fun p4_12_both_languages_name_the_retention_window() {
        for (name in listOf("values", "values-ar")) {
            val text = locale(name)
            for (key in listOf(
                "protection_retention",
                "protection_retention_desc",
                "protection_retention_days",
            )) {
                assertTrue(
                    text.contains("name=\"$key\""),
                    "$name has no $key, so the retention control cannot be drawn there",
                )
            }
            assertTrue(
                Regex("""name="protection_retention_days">[^<]*%1\${'$'}d""").containsMatchIn(text),
                "$name's protection_retention_days carries no %1\$d, so the chosen window cannot be shown",
            )
        }
    }

    /**
     * The privacy promise must agree with the bounds the control offers: a screen that says the window
     * is the owner's, next to a control that starts at a different floor, is the same lie in two edits.
     */
    @Test
    fun p4_12_the_policy_publishes_the_bounds_the_control_uses() {
        val policy = main("kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt")
        assertTrue(
            policy.contains("MIN_OWNER_DAYS") && policy.contains("MAX_OWNER_DAYS"),
            "EvidenceRetentionPolicy publishes no owner bounds, so the screen would have to invent its own " +
                "and could offer a window the sweeper clamps",
        )
        assertTrue(
            policy.contains("if (days <= 0) return default"),
            "fromOwnerDays no longer falls back to the approved windows on a bad preference; expiry " +
                "authorizes deletion, so the fallback is part of the promise",
        )
    }
}
