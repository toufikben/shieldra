package com.shieldra.storage.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations for SHIELDRA security storage.
 *
 * Decision §4.5: the first real schema change is version 2 with an explicit
 * Migration(1, 2) plus migration tests. Schema changes are never hidden
 * inside a destructive fallback (decision §4.3).
 */
object ShieldraDatabaseMigrations {

    /**
     * v1 → v2: adds per-evidence rotation metadata required by decision §3.2.
     *
     * - `key_version`: which Keystore key generation decrypts this evidence.
     * - `aad_version`: which canonical AAD version binds this evidence.
     *
     * Existing rows keep their meaning: they were encrypted under v1 with
     * AAD-v1, so the NOT NULL defaults preserve them without data loss.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE event_evidence_refs " +
                    "ADD COLUMN key_version TEXT NOT NULL DEFAULT 'v1'",
            )
            db.execSQL(
                "ALTER TABLE event_evidence_refs " +
                    "ADD COLUMN aad_version TEXT NOT NULL DEFAULT 'AAD-v1'",
            )
        }
    }

    /**
     * v2 → v3: makes the Panic activation count durable (amendment A-7).
     *
     * - `panic_activation_count`: how many explicit activations one Panic
     *   incident has absorbed.
     * - `panic_last_activated_at_epoch_ms`: when the most recent one arrived.
     *
     * Both are added nullable with no default, so existing rows keep reading
     * as "no activation was recorded". Back-filling 0 or the event's creation
     * time would assert a press that never happened.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE security_events ADD COLUMN panic_activation_count INTEGER")
            db.execSQL("ALTER TABLE security_events ADD COLUMN panic_last_activated_at_epoch_ms INTEGER")
        }
    }

    /** All supported forward migrations, in order. No destructive fallback exists. */
    val all: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
}
