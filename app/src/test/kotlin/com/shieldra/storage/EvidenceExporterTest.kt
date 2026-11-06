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

    @Test
    fun `evidence that is gone is reported instead of counted as exported`() {
        // G-10: the count used to be `included.size` — the items redaction allowed — so a pruned
        // evidence file still counted as exported and the owner was told the archive was saved.
        val partial = EvidenceExporter(
            readEvidence = { if (it == "p1") payloads[it] else null },
            recordAudit = { audited = it },
        )

        val result = partial.export(items, includeLocation = true, passphrase = pass.copyOf(), nowMs = 30, authenticated = true, explicitAction = true)

        assertEquals(1, result.includedCount, "only the payload that was really read is exported")
        assertEquals(2, result.unreadableCount, "the audio and location files were gone")
        assertEquals(1, audited?.includedCount, "the audit records what left, not what was allowed")
    }

    @Test
    fun `an export with nothing readable reports zero rather than a sealed empty success`() {
        val empty = EvidenceExporter(readEvidence = { null })

        val result = empty.export(items, includeLocation = true, passphrase = pass.copyOf(), nowMs = 40, authenticated = true, explicitAction = true)

        assertEquals(0, result.includedCount)
        assertEquals(3, result.unreadableCount)
    }
}
