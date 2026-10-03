package com.shieldra.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events WHERE event_id = :eventId LIMIT 1")
    fun find(eventId: String): SecurityEventEntity?

    /**
     * Newest-first page ordered by when the event happened, not by when its
     * state was last touched, so the list does not reshuffle on delivery.
     */
    @Query("SELECT * FROM security_events ORDER BY created_at_epoch_ms DESC LIMIT :limit")
    fun findRecent(limit: Int): List<SecurityEventEntity>

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

    /**
     * One atomic Panic activation (amendment A-7).
     *
     * Incrementing and stamping happen in a single UPDATE so no window exists
     * in which a concurrent press or a crash could read a stale count and lose
     * an activation. The `state NOT IN (...)` guard makes a terminal event
     * match zero rows, so the caller sees a refusal instead of a silent
     * increment on an incident that has already ended.
     */
    @Query(
        """
        UPDATE security_events
        SET panic_activation_count = COALESCE(panic_activation_count, 0) + 1,
            panic_last_activated_at_epoch_ms = :activatedAtEpochMs
        WHERE event_id = :eventId AND state NOT IN ('DELIVERED', 'FAILED_FINAL', 'EXPIRED')
        """,
    )
    fun incrementPanicActivation(eventId: String, activatedAtEpochMs: Long): Int

    /**
     * Every stored event, oldest decision included (Q-K-2).
     *
     * The sweep needs the whole table because a 90-day-old event is by
     * definition not in the newest page the UI reads.
     */
    @Query("SELECT * FROM security_events")
    fun findAll(): List<SecurityEventEntity>

    /**
     * Drops the events whose metadata window closed (§5.4, Q-K-2).
     *
     * The ids come from the retention plan, never from a window restated in SQL,
     * so 90 days stays decided in exactly one place. Callers must clear the child
     * rows first and must pass a non-empty list.
     */
    @Query("DELETE FROM security_events WHERE event_id IN (:eventIds)")
    fun deleteByIds(eventIds: List<String>): Int
}

@Dao
interface EvidenceReferenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(reference: EvidenceReferenceEntity): Long

    @Query("SELECT * FROM event_evidence_refs")
    fun findAll(): List<EvidenceReferenceEntity>

    @Query("SELECT * FROM event_evidence_refs WHERE event_id = :eventId")
    fun findForEvent(eventId: String): List<EvidenceReferenceEntity>

    /** The evidence rows of a page of events in one query, instead of one query per event. */
    @Query("SELECT * FROM event_evidence_refs WHERE event_id IN (:eventIds) ORDER BY rowid")
    fun findForEvents(eventIds: List<String>): List<EvidenceReferenceEntity>

    @Query("SELECT * FROM event_evidence_refs WHERE evidence_id = :evidenceId LIMIT 1")
    fun findById(evidenceId: String): EvidenceReferenceEntity?

    /**
     * Removes the rows of payloads the retention sweep really deleted, so the record never names a
     * file that is gone. Callers must pass a non-empty list.
     */
    @Query("DELETE FROM event_evidence_refs WHERE evidence_id IN (:evidenceIds)")
    fun deleteByIds(evidenceIds: List<String>): Int

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

    /**
     * Removes the evidence rows of events whose metadata window closed.
     *
     * Cleared explicitly rather than left to the foreign-key cascade, so the
     * sweep does not depend on whether the platform enforces cascades. Callers
     * must pass a non-empty list.
     */
    @Query("DELETE FROM event_evidence_refs WHERE event_id IN (:eventIds)")
    fun deleteForEvents(eventIds: List<String>): Int

    /**
     * Every stored payload with the age of the event that owns it (Q-K-2).
     *
     * The evidence row's own captured_at column is null for every row this app
     * has written, and the payload's file timestamp is not a retention clock: a
     * key rotation rewrites the file and moves that timestamp forward, which
     * would buy an expired record another full window. The event's creation
     * stamp is written once and never rewritten, so it is the age that holds.
     */
    @Query(
        """
        SELECT refs.evidence_id AS evidenceId,
               events.created_at_epoch_ms AS createdAtEpochMs
        FROM event_evidence_refs AS refs
        INNER JOIN security_events AS events ON events.event_id = refs.event_id
        """,
    )
    fun findAllWithEventAges(): List<EvidenceAgeRow>
}

@Dao
interface DeliveryAttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(attempt: DeliveryAttemptEntity): Long

    @Query("SELECT * FROM event_delivery_attempts WHERE event_id = :eventId")
    fun findForEvent(eventId: String): List<DeliveryAttemptEntity>

    /** The attempt rows of a page of events in one query, instead of one query per event. */
    @Query("SELECT * FROM event_delivery_attempts WHERE event_id IN (:eventIds) ORDER BY rowid")
    fun findForEvents(eventIds: List<String>): List<DeliveryAttemptEntity>

    /** Removes the attempt rows of expired events. Callers must pass a non-empty list. */
    @Query("DELETE FROM event_delivery_attempts WHERE event_id IN (:eventIds)")
    fun deleteForEvents(eventIds: List<String>): Int
}
