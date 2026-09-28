package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that [EvidenceKeyPolicy] matches the values required by
 * decisions §2.1 and §2.2 of the Final Security Storage Decisions.
 */
class EvidenceKeyPolicyTest {

    @Test
    fun `alias is versioned and matches decision 2-1`() {
        assertEquals(
            "shieldra_evidence_v1",
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
            "Decision §2.1: key alias must be shieldra_evidence_v1",
        )
    }

    @Test
    fun `policy uses AES-256`() {
        assertEquals(
            256,
            EvidenceKeyPolicy.EVIDENCE_POLICY.keySizeBits,
            "Decision §2.2: key size must be 256 bits",
        )
    }

    @Test
    fun `policy does not require user authentication (decision 2-2)`() {
        assertFalse(
            EvidenceKeyPolicy.EVIDENCE_POLICY.requireUserAuthentication,
            "Decision §2.2: requireUserAuthentication must be false for background protection",
        )
    }

    @Test
    fun `policy does not invalidate on biometric enrollment (decision 2-2)`() {
        assertFalse(
            EvidenceKeyPolicy.EVIDENCE_POLICY.invalidateOnBiometricEnrollment,
            "Decision §2.2: invalidateOnBiometricEnrollment must be false",
        )
    }

    @Test
    fun `rotation target alias is versioned v2 (decision 3-1)`() {
        assertEquals(
            "shieldra_evidence_v2",
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS_V2,
            "Decision §3.1: rotation target alias must be shieldra_evidence_v2",
        )
        assertEquals(
            "shieldra_evidence_v1",
            EvidenceKeyPolicy.aliasForKeyVersion(EvidenceKeyPolicy.KEY_VERSION_V1),
        )
        assertEquals(
            "shieldra_evidence_v2",
            EvidenceKeyPolicy.aliasForKeyVersion(EvidenceKeyPolicy.KEY_VERSION_V2),
        )
        assertFailsWith<IllegalArgumentException> {
            EvidenceKeyPolicy.aliasForKeyVersion("v99")
        }
    }

    @Test
    fun `rotation trigger is 90 days or manual (decision 3-1)`() {
        assertEquals(90L, EvidenceKeyPolicy.ROTATION_INTERVAL_DAYS)
        val day = 24L * 60L * 60L * 1000L
        assertFalse(EvidenceKeyPolicy.rotationDue(0L, 89L * day))
        assertTrue(EvidenceKeyPolicy.rotationDue(0L, 90L * day))
        assertTrue(EvidenceKeyPolicy.rotationDue(0L, 400L * day))
        assertFalse(EvidenceKeyPolicy.rotationDue(10L * day, 5L * day))
    }
}
