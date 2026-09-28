package com.shieldra.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shieldra.storage.room.ShieldraDatabase
import com.shieldra.storage.room.ShieldraDatabaseMigrations
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decisions §4.2–§4.6 on a real device: the marker strings in
 * [StorageRecovery] are matched against the exception wording Room and the
 * framework SQLite layer actually produce, not against invented messages.
 *
 * Each case corrupts only the `user_version` of a test-owned database so a
 * genuine downgrade or migration failure occurs, then opens it through
 * [StorageRecoveryGuard]. The required outcome is RecoveryRequired plus an
 * untouched database file.
 */
@RunWith(AndroidJUnit4::class)
class StorageRecoveryInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShieldraDatabase::class.java,
        emptyList(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun real_room_downgrade_fails_closed_and_preserves_the_database() {
        val databaseFile = createV2DatabaseWithVersion(NEWER_VERSION)
        val result = openThroughGuardAndQuery(databaseFile)

        val recovery = assertRecovery(result)
        assertNotEquals(
            "Room's real downgrade wording must be recognised, not degraded to an " +
                "unknown state. Actual detail: ${recovery.detail}",
            StorageRecoveryReason.UNREADABLE_STATE,
            recovery.reason,
        )
        assertUserVersionUnchanged(NEWER_VERSION)
    }

    @Test
    fun real_room_migration_failure_fails_closed_and_preserves_the_database() {
        // A v2-shaped file claiming to be v1 makes MIGRATION_1_2 run against
        // columns that already exist: the real "duplicate column" failure.
        val databaseFile = createV2DatabaseWithVersion(OLD_VERSION)
        val result = openThroughGuardAndQuery(databaseFile)

        val recovery = assertRecovery(result)
        assertNotEquals(
            "Room's real migration failure wording must be recognised, not degraded " +
                "to an unknown state. Actual detail: ${recovery.detail}",
            StorageRecoveryReason.UNREADABLE_STATE,
            recovery.reason,
        )
        assertTrue("the database file must survive a failed migration", databaseFile.exists())
    }

    private fun createV2DatabaseWithVersion(version: Int): java.io.File {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("PRAGMA user_version = $version")
            close()
        }
        return context.getDatabasePath(TEST_DB)
    }

    private fun openThroughGuardAndQuery(databaseFile: java.io.File): StorageOpenResult<*> {
        val database = Room.databaseBuilder(context, ShieldraDatabase::class.java, TEST_DB)
            .addMigrations(*ShieldraDatabaseMigrations.all.toTypedArray())
            .allowMainThreadQueries()
            .build()
        return try {
            StorageRecoveryGuard.open(databaseFile) {
                database.evidenceReferenceDao().findAll()
            }
        } finally {
            runCatching { database.close() }
        }
    }

    private fun assertRecovery(
        result: StorageOpenResult<*>,
    ): StorageOpenResult.RecoveryRequired<*> {
        assertTrue("storage must not open as Ready: $result", result is StorageOpenResult.RecoveryRequired)
        return result as StorageOpenResult.RecoveryRequired<*>
    }

    /** A destructive fallback would have rewritten the file at the declared version. */
    private fun assertUserVersionUnchanged(expected: Int) {
        val database = SQLiteDatabase.openDatabase(
            context.getDatabasePath(TEST_DB).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            database.rawQuery("PRAGMA user_version", emptyArray()).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(
                    "the failed open must not rewrite the database version",
                    cursor.getInt(0) == expected,
                )
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        private const val TEST_DB = "shieldra-recovery-test.db"
        private const val NEWER_VERSION = 5
        private const val OLD_VERSION = 1
    }
}
