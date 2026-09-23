package com.shieldra.storage

/**
 * Versioned SQL contract for the future Room adapter.
 *
 * This file deliberately contains no database driver and no retention policy.
 * A Room migration must consume this contract only after G4 schema approval.
 */
object StorageSchema {
    const val VERSION: Int = 1

    const val EVENTS_TABLE = "security_events"
    const val EVIDENCE_REFS_TABLE = "event_evidence_refs"
    const val DELIVERY_ATTEMPTS_TABLE = "event_delivery_attempts"

    val CREATE_EVENTS = """
        CREATE TABLE security_events (
            event_id TEXT NOT NULL PRIMARY KEY,
            event_type TEXT NOT NULL,
            state TEXT NOT NULL,
            severity TEXT,
            created_at_epoch_ms INTEGER NOT NULL,
            updated_at_epoch_ms INTEGER NOT NULL,
            resume_stage TEXT,
            expires_at_epoch_ms INTEGER,
            CHECK (length(event_id) > 0),
            CHECK (length(event_type) > 0),
            CHECK (length(state) > 0),
            CHECK (updated_at_epoch_ms >= created_at_epoch_ms)
        )
    """.trimIndent()

    val CREATE_EVIDENCE_REFS = """
        CREATE TABLE event_evidence_refs (
            evidence_id TEXT NOT NULL PRIMARY KEY,
            event_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            content_reference TEXT,
            captured_at_epoch_ms INTEGER,
            FOREIGN KEY (event_id) REFERENCES security_events(event_id) ON DELETE CASCADE,
            CHECK (length(evidence_id) > 0),
            CHECK (length(event_id) > 0),
            CHECK (length(kind) > 0)
        )
    """.trimIndent()

    val CREATE_DELIVERY_ATTEMPTS = """
        CREATE TABLE event_delivery_attempts (
            attempt_id TEXT NOT NULL PRIMARY KEY,
            event_id TEXT NOT NULL,
            channel TEXT NOT NULL,
            status TEXT NOT NULL,
            occurred_at_epoch_ms INTEGER,
            failure_reason TEXT,
            FOREIGN KEY (event_id) REFERENCES security_events(event_id) ON DELETE CASCADE,
            CHECK (length(attempt_id) > 0),
            CHECK (length(event_id) > 0),
            CHECK (length(channel) > 0),
            CHECK (length(status) > 0)
        )
    """.trimIndent()

    const val CREATE_EVENT_STATE_INDEX =
        "CREATE INDEX index_security_events_state ON security_events(state)"
    const val CREATE_EVENT_EXPIRY_INDEX =
        "CREATE INDEX index_security_events_expires_at ON security_events(expires_at_epoch_ms)"
    const val CREATE_EVIDENCE_EVENT_INDEX =
        "CREATE INDEX index_event_evidence_refs_event_id ON event_evidence_refs(event_id)"
    const val CREATE_DELIVERY_EVENT_INDEX =
        "CREATE INDEX index_event_delivery_attempts_event_id ON event_delivery_attempts(event_id)"

    val CREATE_STATEMENTS: List<String> = listOf(
        CREATE_EVENTS,
        CREATE_EVIDENCE_REFS,
        CREATE_DELIVERY_ATTEMPTS,
        CREATE_EVENT_STATE_INDEX,
        CREATE_EVENT_EXPIRY_INDEX,
        CREATE_EVIDENCE_EVENT_INDEX,
        CREATE_DELIVERY_EVENT_INDEX,
    )
}

/** Explicit migration boundary; no destructive migration is provided by default. */
interface StorageMigration {
    val fromVersion: Int
    val toVersion: Int
    fun statements(): List<String>
}

object StorageMigrations {
    val all: List<StorageMigration> = emptyList()
}

fun requireSupportedSchemaVersion(version: Int) {
    require(version in 1..StorageSchema.VERSION) {
        "Unsupported storage schema version: $version"
    }
}
