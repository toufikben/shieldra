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
