package com.shieldra.storage

/**
 * One stored thing, with the only fact the retention windows need: when it was
 * stored. [storedAtEpochMs] is null when the age could not be read, and an
 * artifact of unknown age is never a deletion candidate — expiry authorizes
 * irreversible deletion, and "we could not tell how old this is" is not evidence
 * that it is old.
 */
data class RetainedArtifact(
    val id: String,
    val storedAtEpochMs: Long?,
)

/**
 * The ids whose window has closed, separated by the kind of stored thing they
 * are, because each kind is deleted through a different mechanism.
 */
data class RetentionDeletions(
    val evidenceIds: Set<String>,
    val quarantineIds: Set<String>,
    val eventIds: Set<String>,
)

/**
 * Centralized evidence retention policy (decision §5.4, amendment A-1).
 *
 * §5.4 requires the retention policy to be centralized and adjustable in a
 * policy layer. Every caller reads this object; no retention window is
 * re-stated at a call site, so changing a duration is a change here only.
 *
 * A-1 (2026-09-28) replaced the original "quarantined files: 7 days maximum"
 * with a uniform 30 days, so quarantine and active evidence now expire on the
 * same schedule.
 */
data class EvidenceRetentionPolicy(
    val quarantineRetentionDays: Long,
    val evidenceRetentionDays: Long,
    val eventMetadataRetentionDays: Long,
) {

    init {
        require(quarantineRetentionDays >= 0) { "Quarantine retention cannot be negative" }
        require(evidenceRetentionDays >= 0) { "Evidence retention cannot be negative" }
        require(eventMetadataRetentionDays >= 0) { "Event metadata retention cannot be negative" }
    }

    /**
     * True when a quarantined file has served its full retention window.
     *
     * The boundary is inclusive: at exactly [quarantineRetentionDays] the file
     * is expired. A clock that moves backwards never expires anything, because
     * expiry authorizes irreversible deletion.
     */
    fun isQuarantineExpired(storedAtMs: Long, nowMs: Long): Boolean =
        isExpired(storedAtMs, nowMs, quarantineRetentionDays)

    fun isEvidenceExpired(storedAtMs: Long, nowMs: Long): Boolean =
        isExpired(storedAtMs, nowMs, evidenceRetentionDays)

    fun isEventMetadataExpired(storedAtMs: Long, nowMs: Long): Boolean =
        isExpired(storedAtMs, nowMs, eventMetadataRetentionDays)

    /**
     * The one place a retention window decides a deletion (Q-K-2, §5.4).
     *
     * Every caller asks this instead of comparing dates itself, so no call site
     * can restate a window and drift from the approved 30/30/90. Nothing here
     * deletes; it only names what has served its full window. An artifact whose
     * age could not be read is left out, and a clock that moved backwards lists
     * nothing, because each expiry predicate refuses to compare against a stamp
     * in the future.
     */
    fun planExpired(
        evidenceFiles: List<RetainedArtifact>,
        quarantinedFiles: List<RetainedArtifact>,
        eventRows: List<RetainedArtifact>,
        nowMs: Long,
    ): RetentionDeletions = RetentionDeletions(
        evidenceIds = expiredIds(evidenceFiles, nowMs, ::isEvidenceExpired),
        quarantineIds = expiredIds(quarantinedFiles, nowMs, ::isQuarantineExpired),
        eventIds = expiredIds(eventRows, nowMs, ::isEventMetadataExpired),
    )

    private fun expiredIds(
        artifacts: List<RetainedArtifact>,
        nowMs: Long,
        hasExpired: (Long, Long) -> Boolean,
    ): Set<String> = artifacts
        .mapNotNull { artifact -> artifact.storedAtEpochMs?.let { artifact.id to it } }
        .filter { (_, storedAtMs) -> storedAtMs >= 0L && hasExpired(storedAtMs, nowMs) }
        .map { (id, _) -> id }
        .toSet()

    private fun isExpired(storedAtMs: Long, nowMs: Long, retentionDays: Long): Boolean {
        require(storedAtMs >= 0) { "Stored timestamp cannot be negative" }
        require(nowMs >= 0) { "Current timestamp cannot be negative" }
        if (nowMs < storedAtMs) return false
        return nowMs - storedAtMs >= retentionDays * MILLIS_PER_DAY
    }

    companion object {
        const val QUARANTINE_RETENTION_DAYS: Long = 30L
        const val EVIDENCE_RETENTION_DAYS: Long = 30L
        const val EVENT_METADATA_RETENTION_DAYS: Long = 90L

        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

        /** The approved §5.4 policy, amended by A-1. */
        val default: EvidenceRetentionPolicy = EvidenceRetentionPolicy(
            quarantineRetentionDays = QUARANTINE_RETENTION_DAYS,
            evidenceRetentionDays = EVIDENCE_RETENTION_DAYS,
            eventMetadataRetentionDays = EVENT_METADATA_RETENTION_DAYS,
        )
    }
}
