package com.shieldra.storage

import com.shieldra.storage.EvidenceExportRedaction.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceExportRedactionTest {

    private val everything = Item.entries.toSet()

    @Test
    fun `keys and delivery secrets are never exported`() {
        val out = EvidenceExportRedaction.included(everything, includeLocation = true)
        assertFalse(Item.ENCRYPTION_KEY in out)
        assertFalse(Item.DELIVERY_SECRET in out)
    }

    @Test
    fun `evidence and metadata are exported when present`() {
        val out = EvidenceExportRedaction.included(everything, includeLocation = true)
        assertTrue(out.containsAll(setOf(Item.PHOTO, Item.AUDIO, Item.LOCATION, Item.EVENT_METADATA)))
    }

    @Test
    fun `excluding location drops only the location`() {
        val out = EvidenceExportRedaction.included(everything, includeLocation = false)
        assertFalse(Item.LOCATION in out)
        assertTrue(out.containsAll(setOf(Item.PHOTO, Item.AUDIO, Item.EVENT_METADATA)))
    }

    @Test
    fun `only available items can be included`() {
        val out = EvidenceExportRedaction.included(setOf(Item.PHOTO, Item.ENCRYPTION_KEY), includeLocation = true)
        assertEquals(setOf(Item.PHOTO), out)
    }
}
