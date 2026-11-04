package com.shieldra.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A-45 D3: whoever holds the stolen phone does not open the owner's settings, so one language is not
 * enough. The notice is composed by the app from its own three locales, with the contact repeated once
 * at the end and the owner's own sentence only when he wrote one.
 */
class TheftNoticeTextTest {

    @Test
    fun `all three languages appear, in that order`() {
        val text = TheftNoticeText.compose(
            arabic = "هاتف مقفل",
            english = "Locked phone",
            french = "Telephone verrouille",
            ownerMessage = null,
            contactLine = null,
        )
        assertEquals(
            listOf("هاتف مقفل", "Locked phone", "Telephone verrouille"),
            text.lines().filter(String::isNotBlank),
        )
    }

    @Test
    fun `the contact is written once and last`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = null, contactLine = "Owner contact: +213555123456")
        assertEquals(1, text.split("Owner contact").size - 1)
        assertTrue(text.endsWith("Owner contact: +213555123456"))
    }

    @Test
    fun `a blank owner sentence is skipped rather than leaving an empty block`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = "   ", contactLine = "call me")
        assertFalse(text.lines().any { it.isBlank() && it.isNotEmpty() })
        assertEquals(listOf("AR", "EN", "FR", "call me"), text.lines().filter(String::isNotBlank))
    }

    @Test
    fun `the owner sentence sits before the contact`() {
        val text = TheftNoticeText.compose("AR", "EN", "FR", ownerMessage = "Please return it", contactLine = "Owner contact: x")
        assertTrue(text.indexOf("Please return it") < text.indexOf("Owner contact: x"))
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}
