package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
