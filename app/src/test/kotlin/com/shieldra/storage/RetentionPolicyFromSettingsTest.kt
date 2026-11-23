package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P1-2: the owner's retention setting must decide the windows, not only describe them. */
class RetentionPolicyFromSettingsTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun the_default_setting_reproduces_the_approved_windows() {
        val policy = EvidenceRetentionPolicy.fromOwnerDays(30)

        assertEquals(30L, policy.evidenceRetentionDays)
        assertEquals(30L, policy.quarantineRetentionDays)
        assertEquals(90L, policy.eventMetadataRetentionDays)
    }

    @Test
    fun a_shorter_setting_shortens_every_window_and_keeps_the_three_to_one_ratio() {
        val policy = EvidenceRetentionPolicy.fromOwnerDays(7)

        assertEquals(7L, policy.evidenceRetentionDays)
        assertEquals(7L, policy.quarantineRetentionDays)
        assertEquals(21L, policy.eventMetadataRetentionDays)
        assertTrue(policy.isEvidenceExpired(storedAtMs = 0L, nowMs = 7 * day), "seven days must expire at seven")
        assertFalse(policy.isEvidenceExpired(storedAtMs = 0L, nowMs = 7 * day - 1), "one millisecond short must not")
    }

    @Test
    fun a_nonsense_setting_falls_back_to_the_approved_windows() {
        assertEquals(EvidenceRetentionPolicy.default, EvidenceRetentionPolicy.fromOwnerDays(0))
        assertEquals(EvidenceRetentionPolicy.default, EvidenceRetentionPolicy.fromOwnerDays(-5))
    }
}
