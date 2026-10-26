package com.shieldra.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportAuditLogTest {

    private fun log() = ExportAuditLog(Files.createTempDirectory("audit").resolve("export_audit.log").toFile())

    @Test
    fun `starts empty`() {
        assertTrue(log().lines().isEmpty())
    }

    @Test
    fun `appends one line per export and keeps order`() {
        val log = log()
        log.append(EvidenceExporter.ExportAuditEntry(atEpochMs = 100, includedCount = 3, includedLocation = true))
        log.append(EvidenceExporter.ExportAuditEntry(atEpochMs = 200, includedCount = 2, includedLocation = false))
        assertEquals(
            listOf("100|items=3|location=true", "200|items=2|location=false"),
            log.lines(),
        )
    }

    @Test
    fun `records counts only - never evidence or secrets`() {
        val log = log()
        log.append(EvidenceExporter.ExportAuditEntry(atEpochMs = 1, includedCount = 1, includedLocation = true))
        val text = log.lines().joinToString()
        assertTrue("pass" !in text.lowercase() && "lat" !in text.lowercase())
    }
}
