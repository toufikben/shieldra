package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The safe-zone switch on the Map tab has no name for a screen reader, and its label already exists.
 *
 * The card is titled "Safe zones" and the line under it explains what the switch does — "Alert me when
 * the phone leaves these places while armed" — but the control itself is a bare Material `Switch` in
 * the title's row. Compose keeps the title's `Text` and the `Switch` in separate semantics nodes
 * unless something merges them, so a screen reader lands on the switch and announces only its role and
 * state: "switch, off". Which switch, doing what, is not in the announcement. That is the WCAG 4.1.2
 * failure the whole screen otherwise avoids — every other icon on this tab carries a
 * `contentDescription`, and `safe_zones_title` names the card.
 *
 * The name is in the resources, present in both languages, formatted as a control label: it says what
 * the switch does — keeps the noisy guards quiet inside the marked places — rather than the "Alert on
 * leaving" wording the A-47 decision retired, because leaving a zone no longer starts anything.
 *
 * The test pins both halves: the copy exists in both languages with no format argument to fill, and
 * the control on the Map tab is one that carries that copy as its accessibility name.
 */
class SafeZonesSwitchLabelTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun locale(name: String): String =
        File(root, "app/src/main/res/$name/strings.xml").readText()

    /** The label is written, in both languages, as a plain control name. */
    @Test
    fun p4_15_both_languages_carry_the_switch_label() {
        for (name in listOf("values", "values-ar")) {
            val text = locale(name)
            assertTrue(
                text.contains("name=\"safe_zones_enable\""),
                "$name has no safe_zones_enable, so the switch has nothing to be called",
            )
            assertTrue(
                Regex("""name="safe_zones_enable">[^<]*</string>""").containsMatchIn(text),
                "$name's safe_zones_enable carries a format argument, so it cannot serve as a name",
            )
        }
    }

    /** The control really is the geofence switch, and really is the one that has no name. */
    @Test
    fun p4_15_the_map_switch_is_the_unnamed_control() {
        val map = main("kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt")
        assertTrue(
            map.contains("geofenceEnabled"),
            "the Map tab no longer draws the geofence switch, so this finding must be re-measured",
        )
        assertFalse(
            map.contains("Switch(checked = geofenceEnabled"),
            "the geofence switch is still a bare Material Switch, which a screen reader announces as " +
                "an unnamed switch",
        )
    }

    /** And it is handed the label the resources already hold. */
    @Test
    fun p4_15_the_switch_is_named() {
        val map = main("kotlin/com/shieldra/app/presentation/screen/map/MapScreen.kt")
        assertTrue(
            map.contains("R.string.safe_zones_enable"),
            "nothing on the Map tab refers to safe_zones_enable, so the switch is still unnamed",
        )
    }
}
