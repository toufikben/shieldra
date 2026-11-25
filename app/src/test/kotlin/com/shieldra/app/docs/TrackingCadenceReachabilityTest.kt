package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The tracking cadence is a number two documents say the owner chooses, and no screen offers.
 *
 * `TrackingCadence` opens with "The owner picks a base interval; as the battery runs down this widens
 * the gap", `ProtectionChange.weakens` treats a lazier interval and a shorter run as live weakenings
 * the owner would notice, and the Map tab prints both numbers to him — "every %1$d min, for at least
 * %2$d min" — so he can see the values but not change them. `ProtectionSettingsStore.save` writes both
 * without any bound, and no string names either field.
 *
 * The result is the same shape as the retention and escalation gaps: a policy object that describes an
 * owner choice, a guard that protects it, a screen that displays it, and no control anywhere. The
 * owner's only remaining ways to change the trail are the two defaults he was born with.
 *
 * The test pins the chain: bounds published by the cadence object itself, a control bound to each
 * field inside those bounds, the store clamping to the same bounds, and the copy in both languages.
 */
class TrackingCadenceReachabilityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun locale(name: String): String =
        File(root, "app/src/main/res/$name/strings.xml").readText()

    /** The premise: the trail really is spaced by these two numbers. */
    @Test
    fun p4_13_the_trail_really_runs_on_both_numbers() {
        val service = main("kotlin/com/shieldra/protection/ProtectionService.kt")
        assertTrue(
            service.contains("trackingIntervalMinutes.coerceAtLeast(1)"),
            "the trail no longer uses the owner's interval, so a control would gate nothing",
        )
        assertTrue(
            service.contains("trackingDurationMinutes / intervalMinutes"),
            "the tick count is no longer derived from the owner's duration",
        )
        val cadence = main("kotlin/com/shieldra/protection/TrackingCadence.kt")
        assertTrue(
            cadence.contains("MIN_INTERVAL_MINUTES") && cadence.contains("MAX_INTERVAL_MINUTES") &&
                cadence.contains("MIN_DURATION_MINUTES") && cadence.contains("MAX_DURATION_MINUTES"),
            "TrackingCadence publishes no owner bounds, so a screen would have to invent a range and could " +
                "offer a value the trail refuses",
        )
    }

    /** The control the map tab's numbers imply. */
    @Test
    fun p4_13_the_screen_offers_interval_and_duration() {
        val screen = main(
            "kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt",
        )
        assertTrue(
            screen.contains("draft.trackingIntervalMinutes"),
            "no control shows the interval the map tab prints, so the owner cannot change it",
        )
        assertTrue(
            screen.contains("draft.copy(trackingIntervalMinutes = "),
            "nothing writes trackingIntervalMinutes back into the draft",
        )
        assertTrue(
            screen.contains("draft.trackingDurationMinutes"),
            "no control shows the duration the map tab prints, so the owner cannot change it",
        )
        assertTrue(
            screen.contains("draft.copy(trackingDurationMinutes = "),
            "nothing writes trackingDurationMinutes back into the draft",
        )
        assertTrue(
            screen.contains("TrackingCadence.MIN_INTERVAL_MINUTES") &&
                screen.contains("TrackingCadence.MAX_DURATION_MINUTES"),
            "the steppers do not take their ranges from TrackingCadence, so they could offer a value the " +
                "store clamps and show a number that is not the one in force",
        )
    }

    /** A stored value outside the bounds must come back inside them, or the map tab prints a lie. */
    @Test
    fun p4_13_the_store_clamps_both_to_the_same_bounds() {
        val store = main("kotlin/com/shieldra/protection/ProtectionSettings.kt")
        assertTrue(
            store.contains("KEY_TRACK_INTERVAL, settings.trackingIntervalMinutes.coerceIn("),
            "save() writes the tracking interval unclamped, so a caller can store a value the trail refuses",
        )
        assertTrue(
            store.contains("KEY_TRACK_DURATION, settings.trackingDurationMinutes.coerceIn("),
            "save() writes the tracking duration unclamped, so a caller can store a value the trail refuses",
        )
    }

    /** Both languages name both rows, with the number as a format argument. */
    @Test
    fun p4_13_both_languages_name_the_cadence() {
        for (name in listOf("values", "values-ar")) {
            val text = locale(name)
            for (key in listOf("protection_track_interval", "protection_track_duration")) {
                assertTrue(
                    text.contains("name=\"$key\""),
                    "$name has no $key, so the cadence row cannot be drawn there",
                )
                assertTrue(
                    Regex("""name="$key">[^<]*%1\${'$'}d""").containsMatchIn(text),
                    "$name's $key carries no %1\$d, so the chosen number cannot be shown",
                )
            }
        }
    }
}
