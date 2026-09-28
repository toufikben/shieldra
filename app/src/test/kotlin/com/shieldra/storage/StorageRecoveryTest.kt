package com.shieldra.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Decisions §4.2, §4.3 and §4.4: every Room open failure — unsupported
 * version, downgrade, or migration failure — must produce RecoveryRequired,
 * must never fall back to a destructive path, and must leave the database
 * file on disk for diagnosis and recovery.
 *
 * The classifier is tested with the exception shapes Room and the framework
 * SQLite layer actually throw, wrapped where Room wraps them.
 */
class StorageRecoveryTest {

    @Test
    fun `downgrade request is classified as downgrade`() {
        assertEquals(
            StorageRecoveryReason.DOWNGRADE,
            StorageRecovery.classify(
                IllegalStateException("A migration policy is required to downgrade from 3 to 2"),
            ),
        )
    }

    @Test
    fun `downgrade text in a nested cause is still classified as downgrade`() {
        val wrapped = RuntimeException(
            "could not open database",
            IllegalStateException("Detected database version 3 is newer than the downgrade target 2"),
        )
        assertEquals(StorageRecoveryReason.DOWNGRADE, StorageRecovery.classify(wrapped))
    }

    @Test
    fun `missing or failed migration is classified as migration failure`() {
        assertEquals(
            StorageRecoveryReason.MIGRATION_FAILURE,
            StorageRecovery.classify(
                IllegalStateException("Migration didn't properly handle: security_events"),
            ),
        )
    }

    @Test
    fun `sql exception raised while migrating is classified as migration failure`() {
        val causedBySqlite = IllegalStateException(
            "Encountered validation error whilst opening db: shieldra.db",
            SQLiteFailure("table event_evidence_refs has no column named key_version"),
        )
        assertEquals(StorageRecoveryReason.MIGRATION_FAILURE, StorageRecovery.classify(causedBySqlite))
    }

    @Test
    fun `schema identity mismatch is classified as unsupported version`() {
        assertEquals(
            StorageRecoveryReason.UNSUPPORTED_VERSION,
            StorageRecovery.classify(
                IllegalStateException(
                    "Room cannot verify the data integrity. Looks like you've changed schema " +
                        "but forgot to update the version number",
                ),
            ),
        )
    }

    @Test
    fun `future database version is classified as unsupported version`() {
        assertEquals(
            StorageRecoveryReason.UNSUPPORTED_VERSION,
            StorageRecovery.classify(IllegalStateException("Unsupported database version 9")),
        )
    }

    @Test
    fun `device measured sqlite migration wording is classified as migration failure`() {
        // Captured on the owner RMX3910 (2026-09-28) from a real failed
        // MIGRATION_1_2: Room surfaces the raw SQLite error, so the marker
        // must be the measured wording rather than the word "migration".
        assertEquals(
            StorageRecoveryReason.MIGRATION_FAILURE,
            StorageRecovery.classify(
                RuntimeException(
                    "android.database.sqlite.SQLiteException: duplicate column name: key_version " +
                        "(code 1 SQLITE_ERROR): , while compiling: ALTER TABLE event_evidence_refs " +
                        "ADD COLUMN key_version TEXT NOT NULL DEFAULT 'v1'",
                ),
            ),
        )
    }

    @Test
    fun `an unexplained failure fails closed as unreadable state`() {
        assertEquals(
            StorageRecoveryReason.UNREADABLE_STATE,
            StorageRecovery.classify(IllegalStateException("boom")),
        )
    }

    @Test
    fun `a message-less failure fails closed instead of crashing the classifier`() {
        assertEquals(StorageRecoveryReason.UNREADABLE_STATE, StorageRecovery.classify(RuntimeException()))
    }

    @Test
    fun `detail keeps the originating exception type for diagnosis`() {
        val result = StorageRecoveryGuard.open<Unit>(preservedFile = null) {
            throw IllegalStateException("boom")
        }
        val recovery = assertIs<StorageOpenResult.RecoveryRequired<Unit>>(result)
        assertTrue(recovery.detail.contains("IllegalStateException"), "detail was: ${recovery.detail}")
    }

    @Test
    fun `open failure returns RecoveryRequired and keeps the database file intact`() {
        val databaseFile = File.createTempFile("shieldra-recovery", ".db").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            val result = StorageRecoveryGuard.open<Unit>(preservedFile = databaseFile) {
                throw IllegalStateException("A migration policy is required to downgrade from 3 to 2")
            }
            val recovery = assertIs<StorageOpenResult.RecoveryRequired<Unit>>(result)
            assertEquals(StorageRecoveryReason.DOWNGRADE, recovery.reason)
            assertTrue(databaseFile.exists(), "database must be preserved for recovery")
            assertContentEquals(byteArrayOf(1, 2, 3, 4), databaseFile.readBytes())
        } finally {
            databaseFile.delete()
        }
    }

    @Test
    fun `a non-exception throwable in the open path still fails closed`() {
        val result = StorageRecoveryGuard.open<Unit>(preservedFile = null) {
            throw FakeStorageError()
        }
        assertIs<StorageOpenResult.RecoveryRequired<Unit>>(result)
        assertEquals(StorageRecoveryReason.UNREADABLE_STATE, result.reason)
    }

    @Test
    fun `a successful open is returned as Ready`() {
        val result = StorageRecoveryGuard.open<Unit>(preservedFile = null) { }
        assertIs<StorageOpenResult.Ready<Unit>>(result)
    }

    /** Stands in for the framework SQLite exception type, which is not on the JVM test classpath. */
    private class SQLiteFailure(message: String) : RuntimeException(message)

    private class FakeStorageError : Error("disk disappeared")
}
