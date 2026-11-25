package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The History week total is a count printed from a fixed template.
 *
 * `HistoryScreen` prints `history_week_total` — "%1$d events" — with the seven-day sum. It is a number
 * with a noun attached, and a fixed template cannot decline: one event in the week reads "1 events",
 * and Arabic reads "2 حدث" where the language requires "حدثان" and "3 أحداث" where it requires a
 * different form again. This is the same defect the event ages carried (F-K11, Q-K-4) and the safe-zone
 * counter carried (P3-2), and this repository already prescribes the answer: `plurals.xml` declares
 * `event_age_*` and `safe_zones_count` so every locale answers in its own forms. The comment written
 * for `safe_zones_count` says it outright — "3 zone" and "3 المناطق" are the same defect the event
 * ages had, and Arabic needs its own dual and few forms.
 *
 * The fix is one resource of the right *kind* and one call site that asks the platform for the form
 * matching the count, in neither language a template with a number substituted into it.
 *
 * The test pins the declaration in both locales (Arabic must carry `two` and `few`, which a
 * one/other copy would silently drop), the call site, and the removal of the fixed template.
 */
class HistoryWeekCountPluralTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun res(relative: String): String = File(root, "app/src/main/res/$relative").readText()

    /** The premise: the number printed is a real sum of the seven columns, not an estimate. */
    @Test
    fun p4_17_the_number_shown_is_the_real_week_sum() {
        val screen = main("kotlin/com/shieldra/app/presentation/screen/history/HistoryScreen.kt")
        assertTrue(
            screen.contains("val total = week.sumOf { it.count }"),
            "the week total is no longer summed from the persisted events, so this finding must be " +
                "re-measured against whatever it counts now",
        )
    }

    /** English answers a count the way English declines it: one, and other. */
    @Test
    fun p4_17_english_declares_a_quantity() {
        val plurals = res("values/plurals.xml")
        assertTrue(
            plurals.contains("""<plurals name="history_week_events">"""),
            "English declares no quantity for the week total, so one event still reads \"1 events\"",
        )
        val block = pluralBlock(plurals, "history_week_events")
        assertTrue(block.contains("""quantity="one""""), "the quantity has no singular form")
        assertTrue(block.contains("""quantity="other""""), "the quantity has no plural form")
    }

    /** Arabic needs its own dual and few forms, which a one/other copy would drop. */
    @Test
    fun p4_17_arabic_declares_its_own_forms() {
        val plurals = res("values-ar/plurals.xml")
        assertTrue(
            plurals.contains("""<plurals name="history_week_events">"""),
            "Arabic declares no quantity for the week total, so two events still read \"2 حدث\"",
        )
        val block = pluralBlock(plurals, "history_week_events")
        for (quantity in listOf("one", "two", "few", "many", "other")) {
            assertTrue(
                block.contains("""quantity="$quantity""""),
                "the Arabic quantity is missing the \"$quantity\" form the language requires",
            )
        }
    }

    /** The screen asks the platform for the form that matches the count. */
    @Test
    fun p4_17_the_screen_asks_for_the_matching_form() {
        val screen = main("kotlin/com/shieldra/app/presentation/screen/history/HistoryScreen.kt")
        assertTrue(
            screen.contains("pluralStringResource(R.plurals.history_week_events, total, total)"),
            "the screen still formats the week total from a fixed template instead of passing the count " +
                "as the quantity the locale declines",
        )
    }

    /** And the fixed template is gone from both languages, so nothing can fall back to it. */
    @Test
    fun p4_17_the_fixed_template_is_removed() {
        for (file in listOf("values/strings.xml", "values-ar/strings.xml")) {
            assertFalse(
                res(file).contains("""name="history_week_total""""),
                "$file still declares the fixed week-total template, so a later call site can read it back",
            )
        }
    }

    private fun pluralBlock(plurals: String, name: String): String {
        val start = plurals.indexOf("""<plurals name="$name">""")
        if (start < 0) return ""
        val end = plurals.indexOf("</plurals>", start)
        return if (end < 0) plurals.substring(start) else plurals.substring(start, end)
    }
}
