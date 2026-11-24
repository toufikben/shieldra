package com.shieldra.app.presentation.screen.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P3-2: the location tab's label and the safe-zone card say only what the screen really holds.
 *
 * The tab was labelled "Map" / "الخريطة" and the screen is not a map: it draws no tiles, requests no
 * tile server, and shows a coordinate locator only inside an event's own detail screen. A label that
 * promises a map is read as one — the owner taps it expecting to see the phone on a street, and finds
 * status rows. The honest name is the thing the screen actually reports: the device's location state.
 *
 * The safe-zone card also listed places without ever saying how many, so an owner with three zones and
 * one failing to save could not tell the two apart at a glance. The count is a quantity, so it is a
 * plural resource (an Arabic reader gets its own dual and few forms) rather than a sentence with an
 * "s" bolted on — the same rule F-K11 forced on the event ages.
 *
 * Both claims are about shipped files, so they are read from disk: a JVM test has no Resources object,
 * and the thing being checked is what the phone will actually render.
 */
class MapHonestyTest {

    private val main: File by lazy {
        listOf(File("src/main"), File("app/src/main")).firstOrNull(File::isDirectory)
            ?: error("no app/src/main above ${File(".").absolutePath}")
    }

    private fun file(locale: String, name: String): String =
        File(main, "res/$locale/$name").readText()

    private fun body(locale: String, name: String, key: String): String =
        file(locale, name).substringAfter("name=\"$key\"").substringBefore("</string>")

    @Test
    fun p3_2_the_tab_is_not_named_after_a_map_the_build_never_draws() {
        val en = body("values", "strings.xml", "tab_map")
        val ar = body("values-ar", "strings.xml", "tab_map")
        assertTrue(
            !en.contains("Map", ignoreCase = false),
            "the English tab still promises a map: $en",
        )
        assertTrue(
            !ar.contains("خريطة"),
            "the Arabic tab still promises a map: $ar",
        )
    }

    @Test
    fun p3_2_the_zone_card_counts_its_places_in_both_locales() {
        for (locale in listOf("values", "values-ar")) {
            val text = file(locale, "plurals.xml")
            assertTrue(
                text.contains("name=\"safe_zones_count\""),
                "$locale defines no safe_zones_count plural",
            )
            val block = text.substringAfter("name=\"safe_zones_count\"").substringBefore("</plurals>")
            assertTrue(
                block.contains("%1\$d"),
                "$locale safe_zones_count drops the count argument: $block",
            )
            // Arabic is the reason this is a plural at all: it distinguishes one, two, a few and many.
            if (locale == "values-ar") {
                for (quantity in listOf("zero", "one", "two", "few", "many", "other")) {
                    assertTrue(
                        block.contains("quantity=\"$quantity\""),
                        "Arabic safe_zones_count has no $quantity form",
                    )
                }
            }
        }
    }

    @Test
    fun p3_2_the_map_screen_shows_the_count_it_now_has() {
        val screen = File(
            main,
            "kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt",
        ).readText()
        assertTrue(
            screen.contains("R.plurals.safe_zones_count"),
            "MapScreen never renders the zone count it was given",
        )
    }
}
