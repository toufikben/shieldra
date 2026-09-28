package com.shieldra.storage

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
