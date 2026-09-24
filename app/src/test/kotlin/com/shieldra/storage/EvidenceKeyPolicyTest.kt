package com.shieldra.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies that [EvidenceKeyPolicy] matches the values required by
 * decisions §2.1 and §2.2 of the Final Security Storage Decisions.
 */
class EvidenceKeyPolicyTest {

    @Test
    fun `alias is versioned and matches decision 2-1`() {
        assertEquals(
            "Decision §2.1: key alias must be shieldra_evidence_v1",
            "shieldra_evidence_v1",
            EvidenceKeyPolicy.EVIDENCE_KEY_ALIAS,
        )
    }

    @Test
    fun `policy uses AES-256`() {
        assertEquals(
            "Decision §2.2: key size must be 256 bits",
            256,
            EvidenceKeyPolicy.EVIDENCE_POLICY.keySizeBits,
        )
    }

    @Test
    fun `policy does not require user authentication (decision 2-2)`() {
        assertFalse(
            "Decision §2.2: requireUserAuthentication must be false for background protection",
            EvidenceKeyPolicy.EVIDENCE_POLICY.requireUserAuthentication,
        )
    }

    @Test
    fun `policy does not invalidate on biometric enrollment (decision 2-2)`() {
        assertFalse(
            "Decision §2.2: invalidateOnBiometricEnrollment must be false",
            EvidenceKeyPolicy.EVIDENCE_POLICY.invalidateOnBiometricEnrollment,
        )
    }
}
