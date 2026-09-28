package com.shieldra.storage

import javax.crypto.AEADBadTagException

/**
 * Security failure semantics (decision §6).
 *
 * This object is the single, auditable encoding of the §6 table. Production
 * paths already fail closed; this makes the mapping explicit, exhaustively
 * tested, and gate-checked rather than only implied by scattered try/catch.
 *
 * §6 additionally forbids converting a security/storage failure into any of:
 *   - "create a new key"
 *   - "delete the old data"
 *   - "pretend evidence doesn't exist"
 *   - "continue silently"
 * That prohibition is structural here: [SecurityFailureOutcome] has no
 * non-blocking value, so none of those four can be expressed as a result.
 */
object SecurityFailureSemantics {

    /** Map a named failure condition to the only permitted §6 outcome. */
    fun classify(condition: SecurityFailureCondition): SecurityFailureOutcome = when (condition) {
        SecurityFailureCondition.CORRUPTION -> SecurityFailureOutcome.UNAVAILABLE
        SecurityFailureCondition.MISSING_EVIDENCE -> SecurityFailureOutcome.UNAVAILABLE
        SecurityFailureCondition.MISSING_KEY -> SecurityFailureOutcome.RECOVERY_REQUIRED
        SecurityFailureCondition.AAD_MISMATCH -> SecurityFailureOutcome.UNAVAILABLE
        SecurityFailureCondition.MIGRATION_ERROR -> SecurityFailureOutcome.RECOVERY_REQUIRED
        SecurityFailureCondition.UNSUPPORTED_DB -> SecurityFailureOutcome.RECOVERY_REQUIRED
        SecurityFailureCondition.UNKNOWN_STATE -> SecurityFailureOutcome.FAIL_CLOSED
    }

    /**
     * Best-effort §6 outcome for a concrete throwable. Anything we cannot name
     * maps to UNKNOWN_STATE and therefore fails closed — we never infer a
     * benign result from an unrecognized security error.
     */
    fun outcomeFor(error: Throwable): SecurityFailureOutcome = when (error) {
        is KeyMaterialUnavailableException -> classify(SecurityFailureCondition.MISSING_KEY)
        is AEADBadTagException -> classify(SecurityFailureCondition.AAD_MISMATCH)
        is EvidenceUnavailableException -> classify(SecurityFailureCondition.MISSING_EVIDENCE)
        else -> classify(SecurityFailureCondition.UNKNOWN_STATE)
    }
}

enum class SecurityFailureCondition {
    CORRUPTION,
    MISSING_EVIDENCE,
    MISSING_KEY,
    AAD_MISMATCH,
    MIGRATION_ERROR,
    UNSUPPORTED_DB,
    UNKNOWN_STATE,
}

enum class SecurityFailureOutcome {
    /** Evidence cannot be read; the caller may quarantine the corrupt artifact. */
    UNAVAILABLE,

    /** The corrupt artifact is parked outside the active evidence namespace. */
    QUARANTINE,

    /** Storage/key state needs operator recovery; nothing is created or deleted. */
    RECOVERY_REQUIRED,

    /** Unrecognized state; refuse to proceed rather than guessing. */
    FAIL_CLOSED,
    ;

    /**
     * Every §6 outcome blocks the normal flow. There is deliberately no
     * non-blocking value, which is how the §6 prohibition on silent
     * continuation is encoded rather than merely documented.
     */
    val isBlocking: Boolean get() = true
}
