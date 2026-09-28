package com.shieldra.storage

/**
 * Evidence export policy (decision §5.5).
 *
 * §5.5 makes export **disabled by default** and forbids any hidden bypass.
 * This is the single source of truth the app ships with: an export is permitted
 * only when it has been explicitly enabled **and** every mandatory §5.5
 * prerequisite has been satisfied. Enabling alone is not enough, so a future
 * enablement cannot quietly skip authentication, redaction, auditing, secure
 * temporary handling or the no-plaintext-persistence requirement.
 *
 * Keeping the gate list as an enum lets a test assert it stays in lockstep with
 * the six bullets in the decision document (§14 — no silent policy change).
 */
data class EvidenceExportPolicy(
    val enabled: Boolean = false,
    val satisfiedGates: Set<ExportGate> = emptySet(),
) {
    val isAllowed: Boolean
        get() = enabled && satisfiedGates.containsAll(ExportGate.entries)

    companion object {
        /** The configuration the application runs with: export disabled. */
        val disabled = EvidenceExportPolicy()
    }
}

/** The six §5.5 export prerequisites. All are mandatory before an export may run. */
enum class ExportGate {
    EXPLICIT_USER_ACTION,
    AUTHENTICATION,
    REDACTION_POLICY,
    AUDIT_RECORD,
    SECURE_TEMP_HANDLING,
    NO_PLAINTEXT_PERSISTENCE,
}
