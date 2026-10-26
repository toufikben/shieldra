package com.shieldra.storage

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Orchestrates a §5.5 evidence export, enforcing every one of the six gates:
 *
 *  - EXPLICIT_USER_ACTION / AUTHENTICATION — the caller proves both; without them the export refuses.
 *  - REDACTION_POLICY — [EvidenceExportRedaction] strips keys/secrets and honours the location choice.
 *  - SECURE_TEMP_HANDLING / NO_PLAINTEXT_PERSISTENCE — the archive is assembled entirely in memory and
 *    only the AES-GCM-sealed blob leaves this function, so decrypted evidence never touches the disk.
 *  - AUDIT_RECORD — every run calls [recordAudit].
 *
 * Decryption of each payload is injected so this stays unit-testable off a device.
 */
class EvidenceExporter(
    private val readEvidence: (evidenceId: String) -> ByteArray?,
    private val recordAudit: (ExportAuditEntry) -> Unit = {},
) {

    data class Item(val evidenceId: String, val redactionItem: EvidenceExportRedaction.Item)

    data class ExportAuditEntry(
        val atEpochMs: Long,
        val includedCount: Int,
        val includedLocation: Boolean,
    )

    data class Result(
        val encryptedArchive: ByteArray,
        val includedCount: Int,
        val policy: EvidenceExportPolicy,
    )

    /**
     * Produces the sealed export, or throws [IllegalStateException] if the §5.5 gates are not met.
     * [passphrase] is the owner's archive passphrase; the caller clears it afterwards.
     */
    fun export(
        items: List<Item>,
        includeLocation: Boolean,
        passphrase: CharArray,
        nowMs: Long,
        authenticated: Boolean,
        explicitAction: Boolean,
    ): Result {
        val gates = mutableSetOf(
            ExportGate.REDACTION_POLICY,
            ExportGate.AUDIT_RECORD,
            ExportGate.SECURE_TEMP_HANDLING,
            ExportGate.NO_PLAINTEXT_PERSISTENCE,
        )
        if (explicitAction) gates += ExportGate.EXPLICIT_USER_ACTION
        if (authenticated) gates += ExportGate.AUTHENTICATION
        val policy = EvidenceExportPolicy(enabled = true, satisfiedGates = gates)
        check(policy.isAllowed) { "§5.5: export refused — explicit user action and authentication are required" }

        val availableKinds = items.map { it.redactionItem }.toSet()
        val allowedKinds = EvidenceExportRedaction.included(availableKinds, includeLocation)
        val included = items.filter { it.redactionItem in allowedKinds }

        val plaintextArchive = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                included.forEach { item ->
                    val bytes = readEvidence(item.evidenceId) ?: return@forEach
                    zip.putNextEntry(ZipEntry("${item.redactionItem.name.lowercase()}_${item.evidenceId}"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }
        val sealed = EvidenceArchiveCipher.encrypt(plaintextArchive, passphrase)

        recordAudit(
            ExportAuditEntry(
                atEpochMs = nowMs,
                includedCount = included.size,
                includedLocation = EvidenceExportRedaction.Item.LOCATION in allowedKinds,
            ),
        )
        return Result(encryptedArchive = sealed, includedCount = included.size, policy = policy)
    }
}
