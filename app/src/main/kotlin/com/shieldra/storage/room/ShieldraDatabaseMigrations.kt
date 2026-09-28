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

    /** All supported forward migrations, in order. No destructive fallback exists. */
    val all: List<Migration> = listOf(MIGRATION_1_2)
}
