package com.shieldra.storage

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Security failure semantics (decision §6).
 *
 * §6 is a mapping table from a failure condition to the ONLY permitted outcome,
 * plus a prohibition list: a security/storage failure must never be turned into
 * "create a new key", "delete the old data", "pretend evidence doesn't exist" or
 * "continue silently". These tests pin the table and prove the prohibition is
 * structural: the outcome set contains no non-blocking value, so a silent
 * continuation cannot be expressed at all.
 */
class SecurityFailureSemanticsTest {

    @Test
    fun corruption_is_unavailable() {
        assertEquals(
            SecurityFailureOutcome.UNAVAILABLE,
            SecurityFailureSemantics.classify(SecurityFailureCondition.CORRUPTION),
        )
    }

    @Test
    fun missing_evidence_is_unavailable() {
        assertEquals(
            SecurityFailureOutcome.UNAVAILABLE,
            SecurityFailureSemantics.classify(SecurityFailureCondition.MISSING_EVIDENCE),
        )
    }

    @Test
    fun missing_key_is_recovery_required() {
        assertEquals(
            SecurityFailureOutcome.RECOVERY_REQUIRED,
            SecurityFailureSemantics.classify(SecurityFailureCondition.MISSING_KEY),
            "§6: a missing key must surface as BLOCKED/RECOVERY_REQUIRED, never as a silent gap",
        )
    }

    @Test
    fun aad_mismatch_is_unavailable() {
        assertEquals(
            SecurityFailureOutcome.UNAVAILABLE,
            SecurityFailureSemantics.classify(SecurityFailureCondition.AAD_MISMATCH),
        )
    }

    @Test
    fun migration_and_unsupported_db_are_recovery_required() {
        assertEquals(
            SecurityFailureOutcome.RECOVERY_REQUIRED,
            SecurityFailureSemantics.classify(SecurityFailureCondition.MIGRATION_ERROR),
        )
        assertEquals(
            SecurityFailureOutcome.RECOVERY_REQUIRED,
            SecurityFailureSemantics.classify(SecurityFailureCondition.UNSUPPORTED_DB),
        )
    }

    @Test
    fun unknown_state_fails_closed() {
        assertEquals(
            SecurityFailureOutcome.FAIL_CLOSED,
            SecurityFailureSemantics.classify(SecurityFailureCondition.UNKNOWN_STATE),
            "§6: an unknown state must fail closed, never guess",
        )
    }

    @Test
    fun the_table_covers_every_documented_condition() {
        assertEquals(
            setOf(
                SecurityFailureCondition.CORRUPTION,
                SecurityFailureCondition.MISSING_EVIDENCE,
                SecurityFailureCondition.MISSING_KEY,
                SecurityFailureCondition.AAD_MISMATCH,
                SecurityFailureCondition.MIGRATION_ERROR,
                SecurityFailureCondition.UNSUPPORTED_DB,
                SecurityFailureCondition.UNKNOWN_STATE,
            ),
            SecurityFailureCondition.entries.toSet(),
            "the §6 table has exactly seven rows; a new condition must be mapped explicitly",
        )
    }

    @Test
    fun no_outcome_allows_silent_continuation() {
        // The prohibition is structural: there is no ALLOW/CONTINUE outcome, so
        // every condition lands on a blocking result and none can mean
        // "create a new key", "delete data", "pretend it's absent" or "continue".
        SecurityFailureOutcome.entries.forEach { outcome ->
            assertTrue(outcome.isBlocking, "$outcome must be a blocking outcome (§6 prohibition)")
        }
        assertEquals(4, SecurityFailureOutcome.entries.size)
    }

    @Test
    fun a_missing_key_error_maps_to_recovery_required() {
        val error = KeyMaterialUnavailableException("No Keystore key exists for alias: shieldra_evidence_v1")
        assertEquals(
            SecurityFailureOutcome.RECOVERY_REQUIRED,
            SecurityFailureSemantics.outcomeFor(error),
        )
    }

    @Test
    fun an_aad_tag_failure_maps_to_unavailable_not_recovery() {
        assertEquals(
            SecurityFailureOutcome.UNAVAILABLE,
            SecurityFailureSemantics.outcomeFor(AEADBadTagException("Tag mismatch")),
        )
    }

    @Test
    fun an_unmapped_error_fails_closed() {
        assertEquals(
            SecurityFailureOutcome.FAIL_CLOSED,
            SecurityFailureSemantics.outcomeFor(IllegalStateException("something unrecognized")),
            "§6: any condition we cannot name must fail closed",
        )
    }

    @Test
    fun recovery_required_and_fail_closed_are_never_treated_as_success() {
        assertFalse(SecurityFailureOutcome.RECOVERY_REQUIRED.isBlocking.not())
        assertFalse(SecurityFailureOutcome.FAIL_CLOSED.isBlocking.not())
        assertFalse(SecurityFailureOutcome.UNAVAILABLE.isBlocking.not())
        assertFalse(SecurityFailureOutcome.QUARANTINE.isBlocking.not())
    }
}
