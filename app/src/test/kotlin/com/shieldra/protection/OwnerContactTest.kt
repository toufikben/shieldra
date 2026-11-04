package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The owner contact is shown to a stranger and used for a call action, so "what did he type" must have
 * one answer: a phone, an address, or something the app will not dial.
 */
class OwnerContactTest {

    @Test
    fun `a spaced international number is a phone and dials without the spaces`() {
        val raw = "+213 555 12 34 56"
        assertEquals(ContactKind.PHONE, OwnerContact.kind(raw))
        assertEquals("+213555123456", OwnerContact.normalized(raw))
        assertEquals("tel:+213555123456", OwnerContact.dialUri(raw))
    }

    @Test
    fun `a zero-prefixed number with dashes is still a phone`() {
        val raw = "00213-555-12-34-56"
        assertEquals(ContactKind.PHONE, OwnerContact.kind(raw))
        assertEquals("00213555123456", OwnerContact.normalized(raw))
    }

    @Test
    fun `an address is lowercased and mailed`() {
        val raw = "  Toufik@Example.COM "
        assertEquals(ContactKind.EMAIL, OwnerContact.kind(raw))
        assertEquals("toufik@example.com", OwnerContact.normalized(raw))
        assertEquals("mailto:toufik@example.com", OwnerContact.dialUri(raw))
    }

    @Test
    fun `anything else is unknown and never turned into an intent`() {
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind("my friend"))
        assertNull(OwnerContact.dialUri("my friend"))
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind("+213"))
        assertEquals(ContactKind.UNKNOWN, OwnerContact.kind(""))
    }
}
