package com.shieldra.storage

import com.shieldra.storage.room.ShieldraDatabase
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What one retention sweep actually removed.
 *
 * Event rows are reported as a count because they go in one batch statement,
 * which is the only number SQLite reports; payloads are reported by id because
 * each file is deleted on its own and one refusal must not hide the others.
 * Ids whose deletion was refused are listed in [failedIds] and stay stored:
 * §6 forbids swallowing a security-relevant failure, and a file that could not
 * be deleted is still there tomorrow.
 */
data class RetentionSweepReport(
    val deletedEventRows: Int,
    val deletedEvidenceFileIds: Set<String>,
    val deletedQuarantineFileIds: Set<String>,
    val failedIds: Set<String>,
)

/**
 * Makes the approved retention windows (§5.4, amended by A-1, executed under
 * Q-K-2) decide something real.
 *
 * The owner put automatic expiry in release scope and ruled out a scheduler for
 * it, so this runs only when something is already running: process start, the
 * monitoring service start, and after an event or evidence record is persisted.
 * That is the honest shape of the guarantee — nothing here deletes at the
 * instant a window closes, and no claim may be made that it does. An app that
 * is never opened lets its expired records sit on the disk until it is.
 *
 * Order matters and is deliberate: the Room rows go first, then the payloads. A
 * failed payload delete then leaves a file with no row pointing at it, which the
 * existing reconcile path already knows how to quarantine. The reverse — a row
 * naming a file that was already deleted — would be a dangling reference in the
 * record the user reads.
 *
 * Deletion of event rows clears the child tables explicitly rather than relying
 * on the foreign-key cascade, so the sweep does not depend on whether the
 * platform enforces cascades.
 */
class EvidenceRetentionSweeper(
    private val database: ShieldraDatabase,
    private val evidenceFiles: EncryptedEvidenceFileStore,
    private val policy: EvidenceRetentionPolicy = EvidenceRetentionPolicy.default,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile
    var lastReport: RetentionSweepReport? = null
        private set

    /** A sweep that could not even read the stored set. Kept rather than swallowed. */
    @Volatile
    var lastFailure: Throwable? = null
        private set

    /**
     * Reads every stored age, asks the policy which windows closed, and deletes
     * exactly those. Callers must be off the main thread: the scan is a Room read
     * and the deletions are Room writes.
     */
    fun sweep(): RetentionSweepReport {
        val plan = policy.planExpired(
            evidenceFiles = evidenceAges(),
            quarantinedFiles = evidenceFiles.quarantinedAges(),
            eventRows = database.securityEventDao().findAll().map {
                RetainedArtifact(it.eventId, it.createdAtEpochMs)
            },
            nowMs = nowMillis(),
        )

        val failed = mutableSetOf<String>()
        val deletedRows = deleteExpiredEvents(plan.eventIds, failed)
        val deletedEvidence = deletePayloads(plan.evidenceIds, failed) { evidenceFiles.delete(it) }
        // A payload that is gone takes its reference row with it; otherwise the event would keep
        // naming a file for the remaining 60 days of its own 90-day window.
        deleteReferences(plan.evidenceIds - failed, failed)
        val deletedQuarantine = deletePayloads(plan.quarantineIds, failed) { evidenceFiles.deleteQuarantined(it) }
        return RetentionSweepReport(
            deletedEventRows = deletedRows,
            deletedEvidenceFileIds = deletedEvidence,
            deletedQuarantineFileIds = deletedQuarantine,
            failedIds = failed.toSet(),
        )
    }

    /**
     * A payload's age is the age of the event that owns it, falling back to the
     * file's own timestamp only when no row claims it. Rotation rewrites the file
     * and moves its timestamp forward, so trusting the timestamp alone would let
     * a 90-day interval keep renewing a 30-day window forever.
     */
    private fun evidenceAges(): List<RetainedArtifact> {
        val ownerAges = database.evidenceReferenceDao().findAllWithEventAges()
            .associate { row -> row.evidenceId to row.createdAtEpochMs }
        return evidenceFiles.evidenceAges().map { artifact ->
            RetainedArtifact(artifact.id, ownerAges[artifact.id] ?: artifact.storedAtEpochMs)
        }
    }

    /**
     * The three authorized execution points all dispatch here instead of sweeping
     * inline: the monitoring service delivers samples on the main thread, and Room
     * throws for a query there.
     */
    fun schedule() {
        // Requests that arrive while one is queued collapse into it: a burst of saves runs one sweep,
        // not one thread and one full-table read per save.
        if (!pending.compareAndSet(false, true)) return
        worker.execute {
            pending.set(false)
            try {
                lastReport = sweep()
            } catch (error: Exception) {
                lastFailure = error
            }
        }
    }

    private val pending = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "shieldra-retention").apply { isDaemon = true }
    }

    private fun deleteReferences(evidenceIds: Set<String>, failed: MutableSet<String>) {
        if (evidenceIds.isEmpty()) return
        try {
            database.evidenceReferenceDao().deleteByIds(evidenceIds.toList())
        } catch (error: Exception) {
            failed += evidenceIds
        }
    }

    private fun deleteExpiredEvents(eventIds: Set<String>, failed: MutableSet<String>): Int {
        if (eventIds.isEmpty()) return 0
        val ids = eventIds.toList()
        return try {
            var deleted = 0
            database.runInTransaction {
                database.deliveryAttemptDao().deleteForEvents(ids)
                database.evidenceReferenceDao().deleteForEvents(ids)
                deleted = database.securityEventDao().deleteByIds(ids)
            }
            deleted
        } catch (error: Exception) {
            failed += ids
            0
        }
    }

    private fun deletePayloads(
        ids: Set<String>,
        failed: MutableSet<String>,
        delete: (String) -> Boolean,
    ): Set<String> {
        val deleted = mutableSetOf<String>()
        ids.forEach { id ->
            try {
                if (delete(id)) deleted += id
            } catch (error: Exception) {
                failed += id
            }
        }
        return deleted
    }
}
