package com.shieldra.storage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "security_events",
    indices = [
        Index(value = ["state"], name = "index_security_events_state"),
        Index(value = ["expires_at_epoch_ms"], name = "index_security_events_expires_at"),
    ],
)
data class SecurityEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    val state: String,
    val severity: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "resume_stage")
    val resumeStage: String?,
    @ColumnInfo(name = "expires_at_epoch_ms")
    val expiresAtEpochMs: Long?,
    /**
     * Explicit Panic activations this incident absorbed (amendment A-7).
     *
     * Nullable on purpose: NULL means "no activation was ever recorded", which
     * is different from 0 and must never be back-filled for pre-v3 rows —
     * a zero would claim a press happened on an event that predates counting.
     */
    @ColumnInfo(name = "panic_activation_count")
    val panicActivationCount: Int?,
    @ColumnInfo(name = "panic_last_activated_at_epoch_ms")
    val panicLastActivatedAtEpochMs: Long?,
)

@Entity(
    tableName = "event_evidence_refs",
    foreignKeys = [
        ForeignKey(
            entity = SecurityEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["event_id"], name = "index_event_evidence_refs_event_id")],
)
data class EvidenceReferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "evidence_id")
    val evidenceId: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    val kind: String,
    @ColumnInfo(name = "content_reference")
    val contentReference: String?,
    @ColumnInfo(name = "captured_at_epoch_ms")
    val capturedAtEpochMs: Long?,
    /**
     * Key version that decrypts this evidence (decision §3.2).
     * Pre-rotation rows created by schema v1 default to "v1" via Migration(1, 2).
     */
    @ColumnInfo(name = "key_version", defaultValue = "'v1'")
    val keyVersion: String = "v1",
    /**
     * AAD version bound to this evidence (decision §3.2).
     * Pre-rotation rows created by schema v1 default to "AAD-v1" via Migration(1, 2).
     */
    @ColumnInfo(name = "aad_version", defaultValue = "'AAD-v1'")
    val aadVersion: String = "AAD-v1",
)

@Entity(
    tableName = "event_delivery_attempts",
    foreignKeys = [
        ForeignKey(
            entity = SecurityEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["event_id"], name = "index_event_delivery_attempts_event_id")],
)
data class DeliveryAttemptEntity(
    @PrimaryKey
    @ColumnInfo(name = "attempt_id")
    val attemptId: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    val channel: String,
    val status: String,
    @ColumnInfo(name = "occurred_at_epoch_ms")
    val occurredAtEpochMs: Long?,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String?,
)
