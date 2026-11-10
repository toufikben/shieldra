package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lock-screen notice is three paragraphs, one per language, and the owner's own words only when
 * they exist. A blank paragraph on a lock screen reads as a broken notice, so the joining rule is
 * pinned here rather than trusted to a builder.
 */
class SnatchTextTest {

    @Test
    fun `the three languages lead, one paragraph each, in the owner's order`() {
        val text = SnatchText.compose(
            arabic = "هذا الهاتف مسروق من صاحبه.",
            english = "This phone was taken from its owner.",
            french = "Ce téléphone a été volé à son propriétaire.",
            ownerMessage = null,
            contactLine = null,
        )

        assertEquals(
            listOf(
                "هذا الهاتف مسروق من صاحبه.",
                "This phone was taken from its owner.",
                "Ce téléphone a été volé à son propriétaire.",
            ),
            text.split("\n\n"),
        )
    }

    @Test
    fun `the owner's note and contact follow the languages`() {
        val text = SnatchText.compose(
            arabic = "ع",
            english = "e",
            french = "f",
            ownerMessage = "  Please call me.  ",
            contactLine = "Contact the owner: +213555123456",
        )

        assertEquals(listOf("ع", "e", "f", "Please call me.", "Contact the owner: +213555123456"), text.split("\n\n"))
    }

    @Test
    fun `a blank note or contact leaves no empty paragraph`() {
        val text = SnatchText.compose(
            arabic = "ع",
            english = "e",
            french = "f",
            ownerMessage = "   ",
            contactLine = "",
        )

        assertEquals(listOf("ع", "e", "f"), text.split("\n\n"))
        assertFalse(text.endsWith("\n\n"))
        assertFalse(text.startsWith("\n"))
    }

    @Test
    fun `a language the build has no line for does not leave a gap`() {
        val text = SnatchText.compose(
            arabic = "ع",
            english = " ",
            french = "f",
            ownerMessage = null,
            contactLine = null,
        )

        assertEquals(listOf("ع", "f"), text.split("\n\n"))
        assertTrue(text.contains("f"))
    }
}
