package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceKeyPolicySequenceTest {

    @Test
    fun `existing generations keep their exact aliases`() {
        assertEquals("shieldra_evidence_v1", EvidenceKeyPolicy.aliasForKeyVersion("v1"))
        assertEquals("shieldra_evidence_v2", EvidenceKeyPolicy.aliasForKeyVersion("v2"))
    }

    @Test
    fun `later generations resolve to the same versioned naming scheme`() {
        assertEquals("shieldra_evidence_v3", EvidenceKeyPolicy.aliasForKeyVersion("v3"))
        assertEquals("shieldra_evidence_v10", EvidenceKeyPolicy.aliasForKeyVersion("v10"))
    }

    @Test
    fun `unsupported versions fail closed instead of inventing an alias`() {
        listOf("", "v0", "v01", "v-1", "v", "1", "V1", "v1 ", "shieldra_evidence_v1", "v1.5").forEach { version ->
            assertFailsWith<IllegalArgumentException>("must reject '$version'") {
                EvidenceKeyPolicy.aliasForKeyVersion(version)
            }
        }
    }

    @Test
    fun `supported versions are exactly v followed by a positive generation`() {
        assertTrue(EvidenceKeyPolicy.isSupportedKeyVersion("v1"))
        assertTrue(EvidenceKeyPolicy.isSupportedKeyVersion("v2"))
        assertTrue(EvidenceKeyPolicy.isSupportedKeyVersion("v99"))
        assertFalse(EvidenceKeyPolicy.isSupportedKeyVersion("v0"))
        assertFalse(EvidenceKeyPolicy.isSupportedKeyVersion("v01"))
        assertFalse(EvidenceKeyPolicy.isSupportedKeyVersion("nope"))
    }
}
