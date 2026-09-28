package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Retention policy layer (decision §5.4, amendments A-1).
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
            "A-1 replaced the 7-day quarantine ceiling with a uniform 30 days",
            EvidenceRetentionPolicy.QUARANTINE_RETENTION_DAYS,
            30L,
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
        val policy = EvidenceRetentionPolicy(
            quarantineRetentionDays = 30,
            evidenceRetentionDays = 30,
            eventMetadataRetentionDays = 90,
        )
        val storedAtMs = 1_000_000L

        assertFalse(
            "evidence one second old is nowhere near its deadline",
            policy.isQuarantineExpired(storedAtMs = storedAtMs, nowMs = storedAtMs + 1_000L),
        )
        assertFalse(policy.isEvidenceExpired(storedAtMs = storedAtMs, nowMs = storedAtMs + 1_000L))
    }

    @Test
    fun quarantine_is_expired_exactly_at_thirty_days() {
        val policy = EvidenceRetentionPolicy.default
        val storedAtMs = 0L

        assertFalse(policy.isQuarantineExpired(storedAtMs, day(29)))
        assertTrue("the boundary is inclusive: day 30 is expired", policy.isQuarantineExpired(storedAtMs, day(30)))
        assertTrue(policy.isQuarantineExpired(storedAtMs, day(31)))
    }

    @Test
    fun evidence_is_expired_exactly_at_thirty_days() {
        val policy = EvidenceRetentionPolicy.default
        val storedAtMs = 0L

        assertFalse(policy.isEvidenceExpired(storedAtMs, day(29)))
        assertTrue(policy.isEvidenceExpired(storedAtMs, day(30)))
    }

    @Test
    fun event_metadata_is_expired_exactly_at_ninety_days() {
        val policy = EvidenceRetentionPolicy.default
        val storedAtMs = 0L

        assertFalse(policy.isEventMetadataExpired(storedAtMs, day(89)))
        assertTrue(policy.isEventMetadataExpired(storedAtMs, day(90)))
    }

    @Test
    fun nothing_is_permanently_retained() {
        val policy = EvidenceRetentionPolicy.default
        val storedAtMs = 0L

        assertTrue(
            "a finite retention window is required; there is no permanent retention (5.4)",
            policy.quarantineRetentionDays.isFinite() &&
                policy.evidenceRetentionDays.isFinite() &&
                policy.eventMetadataRetentionDays.isFinite(),
        )
        assertTrue(policy.isQuarantineExpired(storedAtMs, day(365)))
    }

    @Test
    fun a_clock_that_moves_backwards_never_expires_anything() {
        val policy = EvidenceRetentionPolicy.default
        val storedAtMs = day(10)

        assertFalse(
            "a backwards clock must not be treated as expiry (deletion is irreversible)",
            policy.isQuarantineExpired(storedAtMs, day(5)),
        )
        assertFalse(policy.isEvidenceExpired(storedAtMs, day(5)))
        assertFalse(policy.isEventMetadataExpired(storedAtMs, day(5)))
    }

    @Test
    fun negative_timestamps_are_rejected() {
        val policy = EvidenceRetentionPolicy.default

        assertTrue(
            "a negative retention window is a configuration error, not a policy",
            runCatching { EvidenceRetentionPolicy(quarantineRetentionDays = -1) }.isFailure,
        )
        assertTrue(runCatching { policy.isQuarantineExpired(-1L, 0L) }.isFailure)
        assertTrue(runCatching { policy.isEvidenceExpired(0L, -1L) }.isFailure)
        assertTrue(runCatching { policy.isEventMetadataExpired(-1L, 0L) }.isFailure)
    }

    @Test
    fun a_custom_policy_layer_can_shorten_retention_without_touching_call_sites() {
        val strict = EvidenceRetentionPolicy(
            quarantineRetentionDays = 30,
            evidenceRetentionDays = 30,
            eventMetadataRetentionDays = 90,
        )
        val lenient = strict.copy(evidenceRetentionDays = 60)

        assertFalse(lenient.isEvidenceExpired(day(0), day(40)))
        assertTrue(strict.isEvidenceExpired(day(0), day(40)))
    }

    private fun day(days: Long): Long = days * 24L * 60L * 60L * 1000L
}
