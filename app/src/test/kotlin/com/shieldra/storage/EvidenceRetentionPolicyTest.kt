package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Retention policy layer (decision §5.4, amendment A-1).
 *
 * These tests pin the owner's amendment A-1: quarantined files are retained
 * for the same 30 days as active evidence, replacing the original "7 days
 * maximum". The policy is centralized here and never read from a constant at
 * the call site, so a change is a change to this object only.
 */
class EvidenceRetentionPolicyTest {

    @Test
    fun quarantined_files_are_retained_for_thirty_days() {
        assertEquals(
            30L,
            EvidenceRetentionPolicy.QUARANTINE_RETENTION_DAYS,
            "A-1 replaced the 7-day quarantine ceiling with a uniform 30 days",
        )
    }

    @Test
    fun evidence_files_are_retained_for_thirty_days() {
        assertEquals(30L, EvidenceRetentionPolicy.EVIDENCE_RETENTION_DAYS)
    }

    @Test
    fun event_metadata_is_retained_for_ninety_days() {
        assertEquals(90L, EvidenceRetentionPolicy.EVENT_METADATA_RETENTION_DAYS)
    }

    @Test
    fun active_evidence_is_not_deleted_before_its_deadline() {
        val storedAtMs = 1_000_000L

        assertFalse(
            policy().isQuarantineExpired(storedAtMs = storedAtMs, nowMs = storedAtMs + 1_000L),
            "evidence one second old is nowhere near its deadline",
        )
        assertFalse(policy().isEvidenceExpired(storedAtMs = storedAtMs, nowMs = storedAtMs + 1_000L))
    }

    @Test
    fun quarantine_is_expired_exactly_at_thirty_days() {
        val p = policy()
        val storedAtMs = 0L

        assertFalse(p.isQuarantineExpired(storedAtMs, day(29)))
        assertTrue(p.isQuarantineExpired(storedAtMs, day(30)), "the boundary is inclusive: day 30 is expired")
        assertTrue(p.isQuarantineExpired(storedAtMs, day(31)))
    }

    @Test
    fun evidence_is_expired_exactly_at_thirty_days() {
        val p = policy()
        val storedAtMs = 0L

        assertFalse(p.isEvidenceExpired(storedAtMs, day(29)))
        assertTrue(p.isEvidenceExpired(storedAtMs, day(30)))
    }

    @Test
    fun event_metadata_is_expired_exactly_at_ninety_days() {
        val p = policy()
        val storedAtMs = 0L

        assertFalse(p.isEventMetadataExpired(storedAtMs, day(89)))
        assertTrue(p.isEventMetadataExpired(storedAtMs, day(90)))
    }

    @Test
    fun nothing_is_permanently_retained() {
        val p = policy()

        assertTrue(
            p.isQuarantineExpired(0L, day(365)) &&
                p.isEvidenceExpired(0L, day(365)) &&
                p.isEventMetadataExpired(0L, day(365)),
            "every window is finite; there is no permanent retention (§5.4)",
        )
    }

    @Test
    fun a_clock_that_moves_backwards_never_expires_anything() {
        val p = policy()
        val storedAtMs = day(10)

        assertFalse(
            p.isQuarantineExpired(storedAtMs, day(5)),
            "a backwards clock must not be treated as expiry (deletion is irreversible)",
        )
        assertFalse(p.isEvidenceExpired(storedAtMs, day(5)))
        assertFalse(p.isEventMetadataExpired(storedAtMs, day(5)))
    }

    @Test
    fun negative_timestamps_are_rejected() {
        val p = policy()

        assertTrue(
            runCatching {
                EvidenceRetentionPolicy(
                    quarantineRetentionDays = -1,
                    evidenceRetentionDays = 30,
                    eventMetadataRetentionDays = 90,
                )
            }.isFailure,
            "a negative retention window is a configuration error, not a policy",
        )
        assertTrue(runCatching { p.isQuarantineExpired(-1L, 0L) }.isFailure)
        assertTrue(runCatching { p.isEvidenceExpired(0L, -1L) }.isFailure)
        assertTrue(runCatching { p.isEventMetadataExpired(-1L, 0L) }.isFailure)
    }

    @Test
    fun a_custom_policy_layer_can_shorten_retention_without_touching_call_sites() {
        val strict = policy()
        val lenient = strict.copy(evidenceRetentionDays = 60)

        assertFalse(lenient.isEvidenceExpired(day(0), day(40)))
        assertTrue(strict.isEvidenceExpired(day(0), day(40)))
    }

    private fun policy() = EvidenceRetentionPolicy(
        quarantineRetentionDays = 30,
        evidenceRetentionDays = 30,
        eventMetadataRetentionDays = 90,
    )

    private fun day(days: Long): Long = days * 24L * 60L * 60L * 1000L
}
