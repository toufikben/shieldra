package com.shieldra.storage.room

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the explicit Room migration v1 → v2 (decision §4.5, §4.6).
 *
 * The v1 database is created from the exported `1.json` schema, real rows
 * are inserted, and [ShieldraDatabaseMigrations.MIGRATION_1_2] is run and
 * validated against the exported `2.json` schema. There is no destructive
 * fallback to exercise: decision §4.3 forbids it.
 */
@RunWith(AndroidJUnit4::class)
class ShieldraDatabaseMigrationInstrumentedTest {

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
    fun migration_1_2_preserves_rows_and_defaults_rotation_versions() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO security_events " +
                    "(event_id, event_type, state, severity, created_at_epoch_ms, " +
                    "updated_at_epoch_ms, resume_stage, expires_at_epoch_ms) " +
                    "VALUES ('evt-mig-1', 'PANIC', 'INITIATED', 'HIGH', 1750000000000, " +
                    "1750000000000, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO event_evidence_refs " +
                    "(evidence_id, event_id, kind, content_reference, captured_at_epoch_ms) " +
                    "VALUES ('ev-mig-1', 'evt-mig-1', 'PHOTO', 'files/photo.jpg', 1750000000001)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            ShieldraDatabaseMigrations.MIGRATION_1_2,
        )

        migrated.query(
            SimpleQuery(
                "SELECT event_id, kind, content_reference, captured_at_epoch_ms, " +
                    "key_version, aad_version FROM event_evidence_refs " +
                    "WHERE evidence_id = 'ev-mig-1'",
            ),
        ).use { cursor ->
            assertTrue("v1 evidence row must survive the migration", cursor.moveToFirst())
            assertEquals("evt-mig-1", cursor.stringColumn("event_id"))
            assertEquals("PHOTO", cursor.stringColumn("kind"))
            assertEquals("files/photo.jpg", cursor.stringColumn("content_reference"))
            assertEquals(1750000000001L, cursor.longColumn("captured_at_epoch_ms"))
            assertEquals(
                "pre-rotation rows must keep decrypting under the v1 key",
                EvidenceKeyPolicyValues.KEY_VERSION_V1,
                cursor.stringColumn("key_version"),
            )
            assertEquals(
                "pre-rotation rows must keep binding AAD-v1",
                EvidenceKeyPolicyValues.AAD_VERSION_V1,
                cursor.stringColumn("aad_version"),
            )
        }

        migrated.query(
            SimpleQuery("SELECT state, severity FROM security_events WHERE event_id = 'evt-mig-1'"),
        ).use { cursor ->
            assertTrue("v1 event row must survive the migration", cursor.moveToFirst())
            assertEquals("INITIATED", cursor.stringColumn("state"))
            assertEquals("HIGH", cursor.stringColumn("severity"))
        }
        migrated.close()
    }

    @Test
    fun migration_1_2_is_registered_for_the_storage_factory() {
        assertTrue(
            "MIGRATION_1_2 must be part of the migrations wired into Room",
            ShieldraDatabaseMigrations.all.contains(ShieldraDatabaseMigrations.MIGRATION_1_2),
        )
        assertEquals(
            "only one explicit migration path may exist (decision 4-5)",
            1,
            ShieldraDatabaseMigrations.all.size,
        )
    }

    /**
     * Local copies of the policy constants: this instrumented test must not
     * silently follow a policy change; it pins the expected migration values.
     */
    private object EvidenceKeyPolicyValues {
        const val KEY_VERSION_V1: String = "v1"
        const val AAD_VERSION_V1: String = "AAD-v1"
    }

    companion object {
        private const val TEST_DB = "shieldra-migration-test.db"
    }
}

/** Fail loudly instead of letting a null column silently compare as null. */
private fun Cursor.stringColumn(name: String): String {
    val index = getColumnIndexOrThrow(name)
    if (isNull(index)) throw AssertionError("column '$name' is null")
    return getString(index)
}

private fun Cursor.longColumn(name: String): Long {
    val index = getColumnIndexOrThrow(name)
    if (isNull(index)) throw AssertionError("column '$name' is null")
    return getLong(index)
}
