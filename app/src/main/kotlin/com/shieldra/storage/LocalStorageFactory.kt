package com.shieldra.storage

import android.content.Context
import androidx.room.Room
import com.shieldra.storage.room.RoomEventRepository
import com.shieldra.storage.room.ShieldraDatabase
import com.shieldra.storage.room.ShieldraDatabaseMigrations
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
            encryptor: AndroidKeystoreEncryptor = AndroidKeystoreEncryptor(
                applicationContext = context.applicationContext,
            ),
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
            )
                // Explicit forward migrations only; destructive fallback is
                // prohibited for security data (decision §4.3).
                .addMigrations(*ShieldraDatabaseMigrations.all.toTypedArray())
                .build()

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

        /**
         * Opens local storage under decisions §4.2–§4.4: any failure returns
         * RecoveryRequired and leaves the database file on disk. There is no
         * destructive fallback here, and none may be added by callers.
         *
         * Room does not touch the database file until the first query, so the
         * version and migration failures of §4.2–§4.4 surface on that first
         * access, not in this call. The classifier and the guard are verified
         * here; the real Room exception wording is verified only by the
         * instrumented test on an owner device.
         */
        fun openOrRecovery(
            context: Context,
            encryptor: AndroidKeystoreEncryptor = AndroidKeystoreEncryptor(
                applicationContext = context.applicationContext,
            ),
            keyAlias: String = EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
            policy: EncryptionPolicy = EvidenceKeyPolicy.EVIDENCE_POLICY,
            databaseName: String = DEFAULT_DATABASE_NAME,
            evidenceDirectory: File = File(context.noBackupFilesDir, EVIDENCE_DIRECTORY),
        ): StorageOpenResult<LocalShieldraStorage> =
            StorageRecoveryGuard.open(context.getDatabasePath(databaseName)) {
                create(context, encryptor, keyAlias, policy, databaseName, evidenceDirectory)
            }

        private const val DEFAULT_DATABASE_NAME = "shieldra.db"
        private const val EVIDENCE_DIRECTORY = "shieldra-evidence"
    }
}