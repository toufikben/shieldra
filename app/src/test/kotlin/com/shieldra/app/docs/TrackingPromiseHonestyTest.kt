package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The location tab states the cadence of the post-intrusion trail, and it was stating a promise the
 * guard does not keep.
 *
 * `map_tracking_detail` reads "the phone reports its location every N min for M min". That is only
 * true on a full battery. `TrackingCadence.intervalMs` multiplies the owner's interval by two at 30%
 * and by four at 15%, and `ProtectionService.scheduleTrackingTick` recomputes that factor on every
 * tick — which is the whole point of the design (a stolen phone should keep reporting after its charge
 * runs low, not burn the last percent in a burst). So at a low battery the real gap is two to four
 * times the number on screen, and because the tick count is fixed at start the trail also runs two to
 * four times longer than the stated duration.
 *
 * The direction of the error is the dangerous one: the owner reads the line to decide whether the trail
 * is frequent enough to follow a thief, and the line promises more reporting than they will get. The
 * fix is not to drop the figures — an interval and a duration are worth showing — but to say beside
 * them that the gap widens as the battery falls.
 */
class TrackingPromiseHonestyTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun stringValue(name: String, relative: String): String {
        val text = File(root, "app/src/main/res/$relative/strings.xml").readText()
        val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(text)
        assertTrue(match != null, "$relative has no $name")
        return match!!.groupValues[1]
    }

    @Test
    fun p4_6_the_tracking_line_admits_the_gap_widens() {
        for ((locale, marker) in listOf("values" to "widens", "values-ar" to "يتّسع")) {
            val line = stringValue("map_tracking_detail", locale)
            // Both figures stay: the owner still needs the interval and the window they chose.
            assertTrue(
                line.contains("%1\$d") && line.contains("%2\$d"),
                "$locale: map_tracking_detail dropped one of its figures: $line",
            )
            assertTrue(
                line.contains(marker),
                "$locale: map_tracking_detail promises a fixed cadence the battery-aware trail does not keep: $line",
            )
        }
    }

    /**
     * The guard really does widen the gap, so this is not a text fix bolted onto fixed behaviour. If the
     * cadence ever stops scaling with the battery, the sentence has to come back to a fixed promise.
     */
    @Test
    fun p4_6_the_trail_really_scales_with_the_battery() {
        val cadence = File(root, "app/src/main/kotlin/com/shieldra/protection/TrackingCadence.kt").readText()
        assertTrue(cadence.contains("CRITICAL_FACTOR"), "the trail no longer widens at a critical battery")
        assertTrue(cadence.contains("LOW_FACTOR"), "the trail no longer widens at a low battery")
        val service = File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt").readText()
        assertTrue(
            service.contains("TrackingCadence.intervalMs"),
            "the tracking tick no longer asks the battery-aware cadence for its delay",
        )
        assertFalse(
            service.contains("trackingIntervalMinutes.coerceAtLeast(1) * 60_000L\n        trackingHandler.postDelayed"),
            "the tracking tick posts a fixed interval again, which would make the widened-gap sentence false",
        )
    }

    @Test
    fun p4_6_the_screen_still_shows_the_owners_own_figures() {
        val screen = File(
            root,
            "app/src/main/kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt",
        ).readText()
        assertTrue(
            screen.contains("R.string.map_tracking_detail, intervalMinutes, durationMinutes"),
            "the tab no longer passes the owner's interval and duration to the line",
        )
    }
}
