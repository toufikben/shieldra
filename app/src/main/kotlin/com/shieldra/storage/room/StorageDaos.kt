package com.shieldra.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events WHERE event_id = :eventId LIMIT 1")
    fun find(eventId: String): SecurityEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: SecurityEventEntity): Long

    @Query(
        """
        UPDATE security_events
        SET state = :nextState,
            updated_at_epoch_ms = :updatedAtEpochMs,
            resume_stage = :resumeStage,
            expires_at_epoch_ms = :expiresAtEpochMs
        WHERE event_id = :eventId AND state = :expectedState
        """,
    )
    fun updateStateIfExpected(
        eventId: String,
        expectedState: String,
        nextState: String,
        updatedAtEpochMs: Long,
        resumeStage: String?,
        expiresAtEpochMs: Long?,
    ): Int
}

@Dao
interface EvidenceReferenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(reference: EvidenceReferenceEntity): Long

    @Query("SELECT * FROM event_evidence_refs")
    fun findAll(): List<EvidenceReferenceEntity>

    @Query("SELECT * FROM event_evidence_refs WHERE event_id = :eventId")
    fun findForEvent(eventId: String): List<EvidenceReferenceEntity>

    @Query("SELECT * FROM event_evidence_refs WHERE evidence_id = :evidenceId LIMIT 1")
    fun findById(evidenceId: String): EvidenceReferenceEntity?

    /**
     * Marks one evidence row migrated to a new key/AAD version (decision §3.3).
     * Called only after the rotated file was written, verified, and committed;
     * the old key is never deleted by this update (decision §3.3).
     */
    @Query(
        """
        UPDATE event_evidence_refs
        SET key_version = :keyVersion,
            aad_version = :aadVersion
        WHERE evidence_id = :evidenceId
        """,
    )
    fun updateKeyVersion(
        evidenceId: String,
        keyVersion: String,
        aadVersion: String,
    ): Int
}

@Dao
interface DeliveryAttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(attempt: DeliveryAttemptEntity): Long

    @Query("SELECT * FROM event_delivery_attempts WHERE event_id = :eventId")
    fun findForEvent(eventId: String): List<DeliveryAttemptEntity>
}
