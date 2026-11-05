package com.shieldra.app.presentation.screen.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The contact field is where the owner writes the thing a stranger will dial from the lock screen, so
 * the hint has to name the two forms the app understands and the error has to say what happens with
 * anything else (A-45 D3). These are copy tests rather than logic tests: [com.shieldra.protection
 * .OwnerContactTest] covers the accepting part.
 *
 * The resources are read from disk instead of through a context because a JVM test has no context, and
 * because the claim being checked is about the shipped files: a key that exists only in English is a
 * line the Arabic phone never shows.
 */
class OwnerContactCopyTest {

    private val resources: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull(File::isDirectory)
            ?: error("no resource directory under ${File(".").absolutePath}")
    }

    private fun body(locale: String, key: String): String =
        File(resources, "$locale/strings.xml").readText()
            .substringAfter("name=\"$key\"").substringBefore("</string>")

    @Test
    fun `both user interface locales carry the field copy`() {
        val keys = listOf(
            "protection_notice_title",
            "protection_owner_contact_hint",
            "protection_owner_contact_error",
            "protection_theft_notice_note",
        )
        for (locale in listOf("values", "values-ar")) {
            val text = File(resources, "$locale/strings.xml").readText()
            for (key in keys) assertTrue("$locale is missing $key", text.contains("name=\"$key\""))
        }
    }

    @Test
    fun `the hint names a phone with its country code and an address`() {
        for (locale in listOf("values", "values-ar")) {
            val hint = body(locale, "protection_owner_contact_hint")
            assertTrue("$locale hint must show the plus form: $hint", hint.contains("+"))
            assertTrue("$locale hint must name an email: $hint", hint.contains("@"))
        }
    }

    @Test
    fun `the error line says what will not happen instead of claiming a call`() {
        assertTrue(
            "the English error must not promise a call button: ${body("values", "protection_owner_contact_error")}",
            body("values", "protection_owner_contact_error").contains("no call button"),
        )
        assertTrue(
            "the Arabic error must say the same thing: ${body("values-ar", "protection_owner_contact_error")}",
            body("values-ar", "protection_owner_contact_error").contains("زر اتصال"),
        )
    }

    @Test
    fun `the notice explains itself as three languages and one contact line`() {
        val note = body("values", "protection_theft_notice_note")
        for (language in listOf("Arabic", "English", "French")) {
            assertTrue("the note must name $language: $note", note.contains(language))
        }
    }

    @Test
    fun `the french locale stays the notice and nothing else`() {
        // Recorded on purpose: values-fr carries holder-facing copy only. If the owner's UI is ever
        // translated for real, this test is the line that has to change, together with the comment on
        // top of values-fr/strings.xml — not silently, by adding one French string.
        val text = File(resources, "values-fr/strings.xml").readText()
        val keys = Regex("name=\"([A-Za-z0-9_]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("values-fr carries no keys at all", keys.isNotEmpty())
        for (key in keys) {
            assertTrue("values-fr holds a key the notice does not need: $key", key.startsWith("theft_demand_"))
        }
    }
}
