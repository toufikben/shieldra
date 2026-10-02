package com.shieldra.storage

import android.content.Context
import androidx.room.Room
import com.shieldra.storage.room.RoomEventRepository
import com.shieldra.storage.room.RoomRotationMetadata
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
    private val rotation: EvidenceRotationOrchestrator,
    private val retention: EvidenceRetentionSweeper,
) : AutoCloseable {
    fun reconcileEvidence(): EvidenceReconciliationReport =
        evidenceFiles.reconcile(database.evidenceReferenceDao().findAll().map { it.evidenceId }.toSet())

    /**
     * Q-K-2: the last sweep's outcome, or null if none has finished. Kept as
     * state rather than logged away, the same way a failed rotation check is
     * kept — an expiry run that could not read the stored set is information.
     */
    val lastRetentionReport: RetentionSweepReport? get() = retention.lastReport

    /** A retention sweep that could not run at all. */
    val lastRetentionFailure: Throwable? get() = retention.lastFailure

    /**
     * Runs the approved retention windows now. Callers must be off the main
     * thread; the sweep is a Room read plus Room writes and file deletions.
     */
    fun sweepExpiredEvidence(): RetentionSweepReport = retention.sweep()

    /**
     * Q-K-2 authorized execution point. Dispatches the sweep off the calling
     * thread, because two of the three points can be on the main thread and Room
     * throws for a query there. There is no scheduler behind this: the sweep runs
     * when the app is already running, and never at a wall-clock instant.
     */
    fun scheduleRetentionSweep() {
        retention.schedule()
    }

    /**
     * Runs the approved rotation for the whole evidence set (owner protocol §9/§10).
     *
     * Callers must be off the main thread: the eligibility scan and the migration
     * marks are Room reads and writes. A damaged lifecycle record or a failed
     * migration throws/fails closed here and is the caller's to classify; nothing
     * in this path deletes a key.
     */
    fun rotateEvidenceIfNeeded(manual: Boolean): RotationRunReport = rotation.run(manual)

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

            // Rotation composition (protocol §9/§10): the rotator migrates one entry,
            // the orchestrator decides whether the set is due and when a superseded
            // generation may be marked retired. Room remains the reference authority.
            val rotationMetadata = RoomRotationMetadata(database)
            val rotation = EvidenceRotationOrchestrator(
                rotator = EvidenceKeyRotator(
                    files = FileSystemRotationGateway(evidenceDirectory),
                    metadata = rotationMetadata,
                    ciphers = KeystoreRotationCipherFactory(encryptor, policy),
                    aad = RotationAadVersions,
                ),
                scope = rotationMetadata,
                lifecycle = FileBackedKeyLifecycle(FileBackedKeyLifecycle.fileIn(context.noBackupFilesDir)) {
                    System.currentTimeMillis()
                },
                nowMillis = { System.currentTimeMillis() },
            )

            val evidenceFiles = EncryptedEvidenceFileStore(evidenceDirectory, evidenceCipher, evidenceIdProvider)

            // Q-K-2: retention is given the same composition root as rotation, and
            // the repository is told to notify it whenever it commits a record.
            // Nothing here schedules a run: the sweep happens at an execution point
            // that already exists, not on a clock.
            val retention = EvidenceRetentionSweeper(database, evidenceFiles)

            return LocalShieldraStorage(
                database = database,
                eventRepository = RoomEventRepository(database, onRecordPersisted = retention::schedule),
                evidenceFiles = evidenceFiles,
                rotation = rotation,
                retention = retention,
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