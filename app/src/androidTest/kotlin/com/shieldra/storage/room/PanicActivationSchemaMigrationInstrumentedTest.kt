package com.shieldra.storage.room

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the explicit Room migration v2 → v3 (amendment A-7, decisions
 * §4.5/§4.6): the Panic activation count becomes durable metadata stored beside
 * the event.
 *
 * The v2 database is created from the exported `2.json` schema, real rows are
 * inserted, and [ShieldraDatabaseMigrations.MIGRATION_2_3] is run and validated
 * against the exported `3.json` schema. There is no destructive fallback to
 * exercise: decision §4.3 forbids it.
 *
 * The two new columns are nullable on purpose — an event that was never a Panic
 * activation must keep reading as "no activation recorded", not as a zero that
 * implies a press happened.
 */
@RunWith(AndroidJUnit4::class)
class PanicActivationSchemaMigrationInstrumentedTest {

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
    fun migration_2_3_preserves_events_and_leaves_activation_columns_null() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO security_events " +
                    "(event_id, event_type, state, severity, created_at_epoch_ms, " +
                    "updated_at_epoch_ms, resume_stage, expires_at_epoch_ms) " +
                    "VALUES ('evt-v2-1', 'CONFIRMED_SECURITY_EVENT', 'INITIATED', 'HIGH', " +
                    "1750000000000, 1750000000000, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO security_events " +
                    "(event_id, event_type, state, severity, created_at_epoch_ms, " +
                    "updated_at_epoch_ms, resume_stage, expires_at_epoch_ms) " +
                    "VALUES ('evt-v2-2', 'CONFIRMED_SECURITY_EVENT', 'DELIVERED', NULL, " +
                    "1750000000002, 1750000000002, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            ShieldraDatabaseMigrations.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT event_id, state, panic_activation_count, " +
                "panic_last_activated_at_epoch_ms FROM security_events ORDER BY event_id",
        ).use { cursor ->
            assertTrue("both v2 rows must survive", cursor.moveToFirst())

            assertEquals("evt-v2-1", cursor.stringColumn("event_id"))
            assertEquals("INITIATED", cursor.stringColumn("state"))
            assertNull(
                "an event that never recorded a Panic activation must stay NULL, not 0",
                cursor.nullableLongColumn("panic_activation_count"),
            )
            assertNull(
                "no activation means no activation instant",
                cursor.nullableLongColumn("panic_last_activated_at_epoch_ms"),
            )

            assertTrue(cursor.moveToNext())
            assertEquals("evt-v2-2", cursor.stringColumn("event_id"))
            assertEquals("DELIVERED", cursor.stringColumn("state"))
            assertNull(cursor.nullableLongColumn("panic_activation_count"))
            assertNull(cursor.nullableLongColumn("panic_last_activated_at_epoch_ms"))
        }
        migrated.close()
    }

    @Test
    fun the_atomic_activation_increment_refuses_a_terminal_event() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO security_events " +
                    "(event_id, event_type, state, severity, created_at_epoch_ms, " +
                    "updated_at_epoch_ms, resume_stage, expires_at_epoch_ms) " +
                    "VALUES ('evt-live', 'CONFIRMED_SECURITY_EVENT', 'INITIATED', 'HIGH', " +
                    "1750000000000, 1750000000000, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO security_events " +
                    "(event_id, event_type, state, severity, created_at_epoch_ms, " +
                    "updated_at_epoch_ms, resume_stage, expires_at_epoch_ms) " +
                    "VALUES ('evt-done', 'CONFIRMED_SECURITY_EVENT', 'DELIVERED', 'HIGH', " +
                    "1750000000000, 1750000000000, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            ShieldraDatabaseMigrations.MIGRATION_2_3,
        )

        // The very shape the Room DAO uses: one statement that both increments and
        // refuses a non-active event, so no read-then-write window exists for a
        // crash or a concurrent press to slip through. It runs twice on the active
        // row to show the count really accumulates instead of being re-stamped.
        migrated.execSQL(incrementFor("evt-live", 1750000009999L))
        migrated.execSQL(incrementFor("evt-live", 1750000010000L))
        migrated.execSQL(incrementFor("evt-done", 1750000009999L))

        migrated.query(
            "SELECT panic_activation_count, panic_last_activated_at_epoch_ms " +
                "FROM security_events WHERE event_id = 'evt-live'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "a NULL count must become 1 on the first increment, then 2",
                2L,
                cursor.longColumn("panic_activation_count"),
            )
            assertEquals(
                "the last activation instant must be the one just written",
                1750000010000L,
                cursor.longColumn("panic_last_activated_at_epoch_ms"),
            )
        }

        migrated.query(
            "SELECT panic_activation_count FROM security_events WHERE event_id = 'evt-done'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(
                "a terminal event must match no row and leave the column untouched",
                cursor.nullableLongColumn("panic_activation_count"),
            )
        }
        migrated.close()
    }

    /** The single atomic activation statement, as the Room DAO issues it. */
    private fun incrementFor(eventId: String, activatedAtEpochMs: Long): String =
        "UPDATE security_events SET " +
            "panic_activation_count = COALESCE(panic_activation_count, 0) + 1, " +
            "panic_last_activated_at_epoch_ms = $activatedAtEpochMs " +
            "WHERE event_id = '$eventId' AND " +
            "state NOT IN ('DELIVERED', 'FAILED_FINAL', 'EXPIRED')"

    @Test
    fun migration_2_3_is_registered_for_the_storage_factory() {
        assertTrue(
            "MIGRATION_2_3 must be part of the migrations wired into Room",
            ShieldraDatabaseMigrations.all.contains(ShieldraDatabaseMigrations.MIGRATION_2_3),
        )
        assertTrue(
            "the forward chain must stay complete and ordered",
            ShieldraDatabaseMigrations.all.indexOf(ShieldraDatabaseMigrations.MIGRATION_1_2) <
                ShieldraDatabaseMigrations.all.indexOf(ShieldraDatabaseMigrations.MIGRATION_2_3),
        )
    }

    companion object {
        private const val TEST_DB = "shieldra-panic-activation-migration-test.db"
    }
}

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

/** A nullable column must be compared as null explicitly, not as 0. */
private fun Cursor.nullableLongColumn(name: String): Long? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getLong(index)
}
