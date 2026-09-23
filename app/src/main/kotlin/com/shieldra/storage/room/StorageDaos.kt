package com.shieldra.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events WHERE event_id = :eventId LIMIT 1")
    suspend fun find(eventId: String): SecurityEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SecurityEventEntity): Long

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
    suspend fun updateStateIfExpected(
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
    suspend fun insert(reference: EvidenceReferenceEntity): Long

    @Query("SELECT * FROM event_evidence_refs WHERE event_id = :eventId")
    suspend fun findForEvent(eventId: String): List<EvidenceReferenceEntity>
}

@Dao
interface DeliveryAttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attempt: DeliveryAttemptEntity): Long

    @Query("SELECT * FROM event_delivery_attempts WHERE event_id = :eventId")
    suspend fun findForEvent(eventId: String): List<DeliveryAttemptEntity>
}
