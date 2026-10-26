package com.shieldra.storage

import java.io.File

/**
 * The durable §5.5 export audit trail: one append-only line per export in an app-private file.
 * It records when an export happened and how much left, never the evidence, the passphrase, or any
 * location — an audit record that leaked what it audits would defeat its purpose.
 */
class ExportAuditLog(private val file: File) {

    @Synchronized
    fun append(entry: EvidenceExporter.ExportAuditEntry) {
        file.parentFile?.mkdirs()
        file.appendText("${entry.atEpochMs}|items=${entry.includedCount}|location=${entry.includedLocation}\n")
    }

    @Synchronized
    fun lines(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
}
