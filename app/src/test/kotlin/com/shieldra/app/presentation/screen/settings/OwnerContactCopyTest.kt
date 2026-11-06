package com.shieldra.app.presentation.screen.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The contact field feeds the lost-phone notice (/lost), so the hint has to name the two forms the app
 * understands and the error has to say what happens with anything else. These are copy tests rather
 * than logic tests: [com.shieldra.protection.OwnerContactTest] covers the accepting part.
 *
 * Design B (2026-11-06) removed the lock-screen demand notice and its trilingual copy with the lock
 * itself; the field survives because the owner-triggered lost notice still prints it.
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
            for (key in keys) assertTrue(text.contains("name=\"$key\""), "$locale is missing $key")
        }
    }

    @Test
    fun `the hint names a phone with its country code and an address`() {
        for (locale in listOf("values", "values-ar")) {
            val hint = body(locale, "protection_owner_contact_hint")
            assertTrue(hint.contains("+"), "$locale hint must show the plus form: $hint")
            assertTrue(hint.contains("@"), "$locale hint must name an email: $hint")
        }
    }

    @Test
    fun `the error line promises nothing beyond showing the text`() {
        assertTrue(
            body("values", "protection_owner_contact_error").contains("exactly as written"),
            "the English error must say the text is shown as written: ${body("values", "protection_owner_contact_error")}",
        )
        assertTrue(
            body("values-ar", "protection_owner_contact_error").contains("كما كُتب"),
            "the Arabic error must say the same thing: ${body("values-ar", "protection_owner_contact_error")}",
        )
    }

    @Test
    fun `the note explains the lost notice in both locales`() {
        for (locale in listOf("values", "values-ar")) {
            val note = body(locale, "protection_theft_notice_note")
            for (command in listOf("/lost", "/found")) {
                assertTrue(note.contains(command), "$locale note must name $command: $note")
            }
        }
    }
}
