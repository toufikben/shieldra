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
    /** What this phone really holds right now: measured, never estimated (Storage screen). */
    data class Figures(val eventRows: Int, val evidenceFiles: Int, val evidenceBytes: Long)

    fun figures(): Figures = Figures(
        eventRows = database.securityEventDao().count(),
        evidenceFiles = evidenceFiles.evidenceAges().size,
        evidenceBytes = evidenceFiles.payloadBytes(),
    )

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

        /**
         * The owner's confirmed "delete all data" (§5.7, and audit B-3/§5.13): every event row, every
         * evidence file, both evidence keys, the keys that seal the delivery secrets and queued
         * positions, and every value the app has stored in its own preferences. The caller restarts the
         * process afterwards, because nothing may keep using the closed database or the destroyed keys.
         *
         * Preferences are cleared through the SharedPreferences API rather than by unlinking a file,
         * which is the only portable way to reach both the credential-protected store and the
         * device-protected one Direct Boot writes to. Android keeps the emptied file in place; what is
         * checked is that no value survives in it, and a store that still reads back anything is
         * reported as a failure rather than as a completed deletion.
         */
        fun eraseAll(context: Context, open: LocalShieldraStorage?): FactoryResetResult {
            open?.close()
            val app = context.applicationContext
            val encryptor = AndroidKeystoreEncryptor(applicationContext = app)
            val result = FactoryReset(
                evidenceDirectory = File(app.noBackupFilesDir, EVIDENCE_DIRECTORY),
                metadataDatabase = app.getDatabasePath(DEFAULT_DATABASE_NAME),
                keyDeletion = encryptor::deleteEvidenceKeyIrreversibly,
            ).execute(
                aliases = EVIDENCE_KEY_ALIASES + SECRET_KEY_ALIASES,
                confirmed = true,
            )
            FileBackedKeyLifecycle.fileIn(app.noBackupFilesDir).delete()
            return accountForPreferences(result, clearStoredPreferences(app))
        }

        /**
         * Wipes what the owner never sees as "data" but the privacy policy promises to remove: the safe
         * zones that are their home and workplace, the SIM identifiers the guards compare against, the
         * alert channels, the queued captures and alerts. Returns the stores that still read back a
         * value — an empty list is the only honest basis for claiming the deletion happened.
         */
        private fun clearStoredPreferences(app: Context): List<String> {
            val stillHoldingValues = mutableListOf<String>()
            for (name in OWNER_PREFERENCES) {
                if (!wipePreferences(app, name)) stillHoldingValues += name
            }
            val deviceProtected = runCatching { app.createDeviceProtectedStorageContext() }.getOrNull()
            if (deviceProtected != null && !wipePreferences(deviceProtected, DEVICE_PROTECTED_PREFERENCE)) {
                stillHoldingValues += DEVICE_PROTECTED_PREFERENCE
            }
            return stillHoldingValues
        }

        /** Clears one store and reads it back. A store that cannot be reached is a failure, not a pass. */
        private fun wipePreferences(context: Context, name: String): Boolean = runCatching {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            prefs.all.isEmpty()
        }.getOrDefault(false)

        /** A surviving preference value turns a completed reset into a tombstoned one (A-3). */
        private fun accountForPreferences(
            result: FactoryResetResult,
            stillHoldingValues: List<String>,
        ): FactoryResetResult {
            if (stillHoldingValues.isEmpty()) return result
            val failures = stillHoldingValues.map { "$it still holds stored values after the reset" }
            return when (result) {
                is FactoryResetResult.Completed -> FactoryResetResult.Tombstoned(result.deletedAliases, failures)
                is FactoryResetResult.Tombstoned -> result.copy(failures = result.failures + failures)
                is FactoryResetResult.Refused -> result
            }
        }

        private const val DEFAULT_DATABASE_NAME = "shieldra.db"
        private const val EVIDENCE_DIRECTORY = "shieldra-evidence"

        /**
         * Every preferences file the app writes, and the key aliases that seal values inside them.
         * Each entry is owned by the class named beside it; a reset that misses one of them deletes
         * less than the policy in `docs/PRIVACY_POLICY.md` promises, so the list is checked against
         * the sources by `.github/scripts/g4b-review-check.sh` on every run.
         */
        private val OWNER_PREFERENCES = listOf(
            "shieldra_protection",        // ProtectionSettingsStore: armed flag, safe zones, SIM baseline, owner contact
            "shieldra_delivery",          // DeliverySettingsStore: Telegram chat, mail addresses, sealed secret blobs
            "shieldra_pending_alerts",    // PreferencesPendingAlertStore: queued alerts and their sealed positions
            "shieldra_pending_captures",  // PendingCaptures: capture requests still waiting to run
            "shieldra_app_prefs",         // AppSettingsStore: language and appearance the owner chose
            "shieldra_onboarding",        // OnboardingState: which steps this install has been through
            "shieldra_notification",      // LocalEventNotifier: which events were already announced
        )

        /** Direct Boot's own store, in device-protected storage — read before any unlock. */
        private const val DEVICE_PROTECTED_PREFERENCE = "shieldra_boot"

        private val EVIDENCE_KEY_ALIASES = listOf(
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2,
        )

        /** `shieldra_delivery_v1` belongs to DeliverySettingsStore; `shieldra_secret_v1` to SecretText. */
        private val SECRET_KEY_ALIASES = listOf("shieldra_delivery_v1", SecretText.KEY_ALIAS)
    }
}