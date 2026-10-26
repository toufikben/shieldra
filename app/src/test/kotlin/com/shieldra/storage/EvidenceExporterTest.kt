package com.shieldra.storage

import com.shieldra.storage.EvidenceExportRedaction.Item.AUDIO
import com.shieldra.storage.EvidenceExportRedaction.Item.ENCRYPTION_KEY
import com.shieldra.storage.EvidenceExportRedaction.Item.LOCATION
import com.shieldra.storage.EvidenceExportRedaction.Item.PHOTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EvidenceExporterTest {

    private val payloads = mapOf(
        "p1" to "photo-bytes".toByteArray(),
        "a1" to "audio-bytes".toByteArray(),
        "l1" to "36.0|3.0|8|gps".toByteArray(),
        "k1" to "SECRET-KEY".toByteArray(),
    )
    private var audited: EvidenceExporter.ExportAuditEntry? = null
    private val exporter = EvidenceExporter(
        readEvidence = { payloads[it] },
        recordAudit = { audited = it },
    )
    private val items = listOf(
        EvidenceExporter.Item("p1", PHOTO),
        EvidenceExporter.Item("a1", AUDIO),
        EvidenceExporter.Item("l1", LOCATION),
        EvidenceExporter.Item("k1", ENCRYPTION_KEY),
    )
    private val pass = "owner-pass".toCharArray()

    @Test
    fun `refuses without authentication`() {
        assertFailsWith<IllegalStateException> {
            exporter.export(items, includeLocation = true, passphrase = pass.copyOf(), nowMs = 1, authenticated = false, explicitAction = true)
        }
    }

    @Test
    fun `refuses without an explicit user action`() {
        assertFailsWith<IllegalStateException> {
            exporter.export(items, includeLocation = true, passphrase = pass.copyOf(), nowMs = 1, authenticated = true, explicitAction = false)
        }
    }

    @Test
    fun `the key payload is never in the export, and the archive decrypts`() {
        val result = exporter.export(items, includeLocation = true, passphrase = pass.copyOf(), nowMs = 10, authenticated = true, explicitAction = true)
        // Key excluded by redaction: photo + audio + location = 3.
        assertEquals(3, result.includedCount)
        assertTrue(result.policy.isAllowed)
        val zip = EvidenceArchiveCipher.decrypt(result.encryptedArchive, pass.copyOf())
        val text = String(zip)
        assertTrue("SECRET-KEY" !in text, "the encryption key must never appear in the export")
    }

    @Test
    fun `excluding location drops it from the export and the audit`() {
        audited = null
        val result = exporter.export(items, includeLocation = false, passphrase = pass.copyOf(), nowMs = 20, authenticated = true, explicitAction = true)
        assertEquals(2, result.includedCount) // photo + audio only
        assertEquals(false, audited?.includedLocation)
    }
}
