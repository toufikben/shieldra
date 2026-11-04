package com.shieldra.protection

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The theft notice is the only text this app shows to a person it does not know, in a moment of
 * conflict. Three locale files must carry the same keys, none may promise something the app cannot do
 * (it wipes nothing and calls no police), and the placeholders the app fills must exist exactly once.
 *
 * Reads the resource files directly: no Android, no device.
 */
class TheftDemandCopyTest {

    private val res: File = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull(File::isDirectory)
        ?: File("src/main/res")

    private val locales = listOf("values", "values-ar", "values-fr")

    private fun strings(locale: String): String = File(res, "$locale/strings.xml").readText()

    private fun body(locale: String, key: String): String =
        strings(locale).substringAfter("name=\"$key\"").substringBefore("</string>")

    @Test
    fun `every locale carries the same theft notice keys`() {
        val keys = listOf(
            "theft_demand_channel",
            "theft_demand_title",
            "theft_demand_body",
            "theft_demand_owner_message",
            "theft_demand_contact",
            "theft_demand_call",
        )
        for (locale in locales) {
            val text = strings(locale)
            for (key in keys) {
                assertTrue(text.contains("name=\"$key\""), "$locale is missing $key")
            }
        }
    }

    @Test
    fun `the notice never claims something the app cannot do`() {
        for (locale in locales) {
            val text = body(locale, "theft_demand_body").lowercase()
            assertTrue(!text.contains("wipe") && !text.contains("effacer"), "$locale must not promise a wipe")
            assertTrue(!text.contains("police") && !text.contains("شرطة"), "$locale must not promise the police")
            assertTrue(text.isNotBlank(), "$locale has no notice body")
        }
    }

    @Test
    fun `the contact and owner keys take exactly one placeholder in every locale`() {
        for (key in listOf("theft_demand_contact", "theft_demand_owner_message")) {
            for (locale in locales) {
                assertEquals(1, Regex("%1\\$s").findAll(body(locale, key)).count(), "$locale:$key")
            }
        }
    }

    @Test
    fun `the three notice bodies are different copy, not one language pasted twice`() {
        val arabic = body("values-ar", "theft_demand_body")
        val english = body("values", "theft_demand_body")
        val french = body("values-fr", "theft_demand_body")
        assertTrue(arabic != english && french != english && arabic != french)
    }
}
