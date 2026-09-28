package com.shieldra.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Evidence export policy (decision §5.5).
 *
 * §5.5 makes export **disabled by default** with no hidden bypass. These tests
 * pin that invariant: the default policy refuses, enabling alone is not
 * enough, and the only way to allow an export is to satisfy every mandatory
 * §5.5 gate. The gate list is asserted against the decision text so a future
 * enablement cannot quietly drop one of the six requirements.
 */
class EvidenceExportPolicyTest {

    @Test
    fun export_is_disabled_by_default() {
        assertFalse(
            EvidenceExportPolicy().isAllowed,
            "§5.5: EXPORT_DISABLED — no export may run without explicit enablement",
        )
        assertFalse(
            EvidenceExportPolicy.disabled.isAllowed,
            "the shared disabled instance is the default the app ships with",
        )
    }

    @Test
    fun enabling_without_satisfying_every_gate_still_refuses() {
        val halfEnabled = EvidenceExportPolicy(
            enabled = true,
            satisfiedGates = setOf(ExportGate.EXPLICIT_USER_ACTION, ExportGate.AUTHENTICATION),
        )
        assertFalse(
            halfEnabled.isAllowed,
            "user action + authentication alone must not permit an export (§5.5 lists six gates)",
        )
    }

    @Test
    fun missing_the_plaintext_persistence_gate_alone_blocks_export() {
        val almostAll = ExportGate.entries.toSet() - ExportGate.NO_PLAINTEXT_PERSISTENCE
        assertFalse(
            EvidenceExportPolicy(enabled = true, satisfiedGates = almostAll).isAllowed,
            "dropping any single gate reopens a plaintext-leak path and must refuse",
        )
    }

    @Test
    fun all_six_gates_enabled_is_the_only_permitted_state() {
        assertTrue(
            EvidenceExportPolicy(enabled = true, satisfiedGates = ExportGate.entries.toSet()).isAllowed,
            "with every §5.5 gate satisfied an export is the only allowed configuration",
        )
    }

    @Test
    fun the_gate_set_matches_the_six_requirements_in_the_decision() {
        assertEquals(
            6,
            ExportGate.entries.size,
            "§5.5 enumerates exactly six export prerequisites",
        )
        assertEquals(
            setOf(
                ExportGate.EXPLICIT_USER_ACTION,
                ExportGate.AUTHENTICATION,
                ExportGate.REDACTION_POLICY,
                ExportGate.AUDIT_RECORD,
                ExportGate.SECURE_TEMP_HANDLING,
                ExportGate.NO_PLAINTEXT_PERSISTENCE,
            ),
            ExportGate.entries.toSet(),
            "the gate list must stay in lockstep with the §5.5 bullet list",
        )
    }
}
