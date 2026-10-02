package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Q-K-2 (owner answer, 2026-10-02): the approved retention windows are in
 * release scope, so they have to decide something real. The plan is pure: it
 * turns stored ages and a clock into the ids whose window has closed, and it is
 * the only place a window is read.
 *
 * Boundary is inclusive, because [EvidenceRetentionPolicy] already defines
 * expiry that way and expiry authorizes irreversible deletion. An artifact with
 * no known age is never deletable, and a clock that moved backwards deletes
 * nothing.
 */
class EvidenceRetentionSweepTest {
    private val day = 24L * 60L * 60L * 1000L
    private val policy = EvidenceRetentionPolicy.default
    private val now = 1_700_000_000_000L

    @Test
    fun qk2_evidence_exactly_thirty_days_old_is_expired() {
        val plan = policy.planExpired(
            evidenceFiles = listOf(RetainedArtifact("e-old", now - 30 * day)),
            quarantinedFiles = emptyList(),
            eventRows = emptyList(),
            nowMs = now,
        )
        assertEquals(setOf("e-old"), plan.evidenceIds)
    }

    @Test
    fun qk2_evidence_younger_than_thirty_days_is_kept() {
        val plan = policy.planExpired(
            evidenceFiles = listOf(RetainedArtifact("e-new", now - 30 * day + 1)),
            quarantinedFiles = emptyList(),
            eventRows = emptyList(),
            nowMs = now,
        )
        assertTrue(plan.evidenceIds.isEmpty(), "a young evidence file must survive")
    }

    @Test
    fun qk2_quarantined_file_exactly_thirty_days_old_is_expired() {
        val plan = policy.planExpired(
            evidenceFiles = emptyList(),
            quarantinedFiles = listOf(RetainedArtifact("q-old", now - 30 * day)),
            eventRows = emptyList(),
            nowMs = now,
        )
        assertEquals(setOf("q-old"), plan.quarantineIds)
    }

    @Test
    fun qk2_event_metadata_exactly_ninety_days_old_is_expired() {
        val plan = policy.planExpired(
            evidenceFiles = emptyList(),
            quarantinedFiles = emptyList(),
            eventRows = listOf(
                RetainedArtifact("ev-old", now - 90 * day),
                RetainedArtifact("ev-new", now - 90 * day + 1),
            ),
            nowMs = now,
        )
        assertEquals(setOf("ev-old"), plan.eventIds)
    }

    @Test
    fun qk2_artifact_with_unknown_age_is_never_deletable() {
        val plan = policy.planExpired(
            evidenceFiles = listOf(RetainedArtifact("e-unknown", null)),
            quarantinedFiles = listOf(RetainedArtifact("q-unknown", null)),
            eventRows = listOf(RetainedArtifact("ev-unknown", null)),
            nowMs = now,
        )
        assertEquals(RetentionDeletions(emptySet(), emptySet(), emptySet()), plan)
    }

    @Test
    fun qk2_clock_that_moved_backwards_deletes_nothing() {
        val plan = policy.planExpired(
            evidenceFiles = listOf(RetainedArtifact("e-future", now + 31 * day)),
            quarantinedFiles = listOf(RetainedArtifact("q-future", now + 31 * day)),
            eventRows = listOf(RetainedArtifact("ev-future", now + 91 * day)),
            nowMs = now,
        )
        assertEquals(RetentionDeletions(emptySet(), emptySet(), emptySet()), plan)
    }

    @Test
    fun qk2_windows_stay_the_approved_thirty_thirty_ninety() {
        assertEquals(30L, policy.evidenceRetentionDays)
        assertEquals(30L, policy.quarantineRetentionDays)
        assertEquals(90L, policy.eventMetadataRetentionDays)
    }
}
