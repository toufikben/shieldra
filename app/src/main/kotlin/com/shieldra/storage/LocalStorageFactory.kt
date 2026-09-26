package com.shieldra.storage

import android.content.Context
import androidx.room.Room
import com.shieldra.storage.room.RoomEventRepository
import com.shieldra.storage.room.ShieldraDatabase
import java.io.File

/**
 * Explicit local-storage composition root.
 * It is intentionally not installed from Application yet: runtime rollout and
 * Android Keystore behavior still require owner-device verification.
 */
class LocalShieldraStorage private constructor(
    val database: ShieldraDatabase,
    val eventRepository: RoomEventRepository,
    val evidenceFiles: EncryptedEvidenceFileStore,
) : AutoCloseable {
    fun reconcileEvidence(): EvidenceReconciliationReport =
        evidenceFiles.reconcile(database.evidenceReferenceDao().findAll().map { it.evidenceId }.toSet())

    override fun close() {
        database.close()
    }

    companion object {
        fun create(
            context: Context,
            encryptor: AndroidKeystoreEncryptor = AndroidKeystoreEncryptor(),
            keyAlias: String = EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
            policy: EncryptionPolicy = EvidenceKeyPolicy.EVIDENCE_POLICY,
            databaseName: String = DEFAULT_DATABASE_NAME,
            evidenceDirectory: File = File(context.noBackupFilesDir, EVIDENCE_DIRECTORY),
        ): LocalShieldraStorage {
            require(evidenceDirectory.canonicalFile.toPath().startsWith(context.noBackupFilesDir.canonicalFile.toPath())) {
                "Evidence directory must remain inside Context.noBackupFilesDir"
            }
            val database = Room.databaseBuilder(
                context.applicationContext,
                ShieldraDatabase::class.java,
                databaseName,
            ).build()

            // Evidence-specific cipher with mandatory AAD binding
            val evidenceCipher = KeystoreEvidenceCipherWithAad(encryptor, keyAlias, policy)

            // AAD provider: canonical AAD-v1 for each evidenceId
            val evidenceIdProvider: (String) -> ByteArray = { evidenceId ->
                EvidenceAadBuilder.forEvidence(evidenceId)
            }

            return LocalShieldraStorage(
                database = database,
                eventRepository = RoomEventRepository(database),
                evidenceFiles = EncryptedEvidenceFileStore(evidenceDirectory, evidenceCipher, evidenceIdProvider),
            )
        }

        private const val DEFAULT_DATABASE_NAME = "shieldra.db"
        private const val EVIDENCE_DIRECTORY = "shieldra-evidence"
    }
}