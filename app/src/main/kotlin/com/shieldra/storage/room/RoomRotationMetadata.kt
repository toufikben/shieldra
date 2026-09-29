package com.shieldra.storage.room

import com.shieldra.storage.RotationMetadata
import com.shieldra.storage.RotationMetadataGateway
import com.shieldra.storage.RotationScopeGateway

/**
 * Room-backed rotation metadata (decision §3.2, §3.4).
 *
 * Versions are read from `event_evidence_refs.key_version` / `aad_version`
 * and are marked only after the rotated file was committed and verified.
 * Missing rows fail closed (null) instead of inventing versions.
 *
 * [RotationScopeGateway] is what makes a rotation decidable for the whole set
 * rather than one entry at a time (owner protocol §10): a generation may only be
 * retired once this scan proves no stored row still references it.
 */
class RoomRotationMetadata(
    private val database: ShieldraDatabase,
) : RotationMetadataGateway, RotationScopeGateway {
    private val evidence = database.evidenceReferenceDao()

    override fun versionsOf(evidenceId: String): RotationMetadata? =
        evidence.findById(evidenceId)?.let {
            RotationMetadata(keyVersion = it.keyVersion, aadVersion = it.aadVersion)
        }

    override fun all(): Map<String, RotationMetadata> =
        evidence.findAll().associate { reference ->
            reference.evidenceId to RotationMetadata(
                keyVersion = reference.keyVersion,
                aadVersion = reference.aadVersion,
            )
        }

    override fun markMigrated(evidenceId: String, keyVersion: String, aadVersion: String) {
        val changed = evidence.updateKeyVersion(evidenceId, keyVersion, aadVersion)
        check(changed == 1) { "Rotation mark affected $changed rows for $evidenceId" }
    }
}
