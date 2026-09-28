package com.shieldra.storage.room

import com.shieldra.storage.RotationMetadata
import com.shieldra.storage.RotationMetadataGateway

/**
 * Room-backed rotation metadata (decision §3.2, §3.4).
 *
 * Versions are read from `event_evidence_refs.key_version` / `aad_version`
 * and are marked only after the rotated file was committed and verified.
 * Missing rows fail closed (null) instead of inventing versions.
 */
class RoomRotationMetadata(
    private val database: ShieldraDatabase,
) : RotationMetadataGateway {
    private val evidence = database.evidenceReferenceDao()

    override fun versionsOf(evidenceId: String): RotationMetadata? =
        evidence.findById(evidenceId)?.let {
            RotationMetadata(keyVersion = it.keyVersion, aadVersion = it.aadVersion)
        }

    override fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String) {
        val changed = evidence.updateKeyVersion(evidenceId, keyVersion, aadVersion)
        check(changed == 1) { "Rotation mark affected $changed rows for $evidenceId" }
    }
}
