package com.shieldra.storage

/**
 * Production rotation trigger and old-key retirement lifecycle
 * (owner protocol §9/§10, decisions §3.1–§3.4).
 *
 * The rotator in [EvidenceKeyRotator] knows how to migrate one evidence entry.
 * This file is what was missing: who decides that a rotation should happen, over
 * which entries, and when the superseded key version may be marked RETIRED.
 *
 * Retirement ordering is fixed by §10 and is not negotiable at a call site:
 * identify every eligible entry, migrate all of them, verify every migration,
 * prove zero remaining references to the old key version, and only then mark it
 * RETIRED. Irreversible destruction is deliberately absent from this type —
 * it is an open owner decision (Q-C8), not an engineering default.
 */

/** Why a rotation run started. */
enum class RotationTrigger {
    INTERVAL_ELAPSED,
    MANUAL,
}

/** How a rotation run ended. Every non-[ROTATED] outcome leaves the old key active. */
enum class RotationTriggerOutcome {
    /** The interval has not elapsed and no manual trigger was given; nothing was touched. */
    NOT_DUE,

    /** Every eligible entry migrated and verified, and the old key version is now RETIRED. */
    ROTATED,

    /** At least one entry failed. Migrated entries stay migrated; nothing retires. */
    PARTIALLY_MIGRATED,

    /** Trigger fired but no entry is on the active key version, so no new key is created. */
    NO_ELIGIBLE_EVIDENCE,

    /** Entries migrated but a reference to the old version survives, or the lifecycle write failed. */
    RETIREMENT_BLOCKED,

    /** The active key version has no approved successor alias; fail closed rather than invent one. */
    NO_APPROVED_SUCCESSOR,
}

/** Result of one orchestrator run. */
data class RotationRunReport(
    val outcome: RotationTriggerOutcome,
    val trigger: RotationTrigger?,
    val migrated: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val detail: String? = null,
)

/** Everything known about the stored evidence key generations. */
data class KeyLifecycleSnapshot(
    val activeKeyVersion: String,
    val retiredKeyVersions: Set<String>,
    val lastRotationEpochMs: Long,
)

/** Enumerates stored evidence metadata so eligibility can be decided for the whole set. */
interface RotationScopeGateway {
    fun all(): Map<String, RotationMetadata>
}

/**
 * Durable key-generation state. Deliberately has no deletion surface: retiring a
 * version is a state transition, and destroying Keystore material happens only in
 * the confirmed factory-reset path (§5.7).
 */
interface KeyLifecycleGateway {
    fun snapshot(): KeyLifecycleSnapshot

    /** The approved successor version to rotate into, or null when none is approved. */
    fun nextKeyVersion(current: String): String?

    /**
     * Promotes [toKeyVersion] to active and marks [fromKeyVersion] retired in one
     * durable write. Throwing here must leave the old version active.
     */
    fun completeRotation(fromKeyVersion: String, toKeyVersion: String, atEpochMs: Long)
}

/**
 * Drives one rotation of the whole evidence set.
 *
 * The trigger policy is exactly the approved one (decisions §3.1): 90 days elapsed
 * since the last completed rotation, or an explicit manual request. Everything
 * else fails closed — an unknown active version, a missing successor alias, a
 * corrupted entry, or a surviving reference all leave the old key in service.
 */
class EvidenceRotationOrchestrator(
    private val rotator: EvidenceKeyRotator,
    private val scope: RotationScopeGateway,
    private val lifecycle: KeyLifecycleGateway,
    private val nowMillis: () -> Long,
) {
    fun run(manual: Boolean): RotationRunReport {
        val now = nowMillis()
        val snapshot = lifecycle.snapshot()
        val trigger = when {
            manual -> RotationTrigger.MANUAL
            EvidenceKeyPolicy.rotationDue(snapshot.lastRotationEpochMs, now) -> RotationTrigger.INTERVAL_ELAPSED
            else -> return RotationRunReport(RotationTriggerOutcome.NOT_DUE, trigger = null)
        }

        val fromVersion = snapshot.activeKeyVersion
        val toVersion = try {
            lifecycle.nextKeyVersion(fromVersion)
        } catch (error: Exception) {
            return blocked(trigger, "successor lookup failed: ${error.message}")
        }
        if (toVersion == null) {
            return RotationRunReport(
                RotationTriggerOutcome.NO_APPROVED_SUCCESSOR,
                trigger,
                detail = "no approved key version succeeds '$fromVersion'",
            )
        }

        val eligible = try {
            scope.all().filterValues { it.keyVersion == fromVersion }.keys.sorted()
        } catch (error: Exception) {
            return blocked(trigger, "eligible evidence scan failed: ${error.message}")
        }
        if (eligible.isEmpty()) {
            return RotationRunReport(
                RotationTriggerOutcome.NO_ELIGIBLE_EVIDENCE,
                trigger,
                detail = "no entry is stored under '$fromVersion'",
            )
        }

        val migrated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        eligible.forEach { evidenceId ->
            when (
                val result = rotator.rotate(
                    RotationRequest(
                        evidenceId = evidenceId,
                        fromKeyVersion = fromVersion,
                        toKeyVersion = toVersion,
                        fromAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
                        toAadVersion = EvidenceKeyPolicy.AAD_VERSION_V1,
                    ),
                )
            ) {
                is RotationResult.Migrated, is RotationResult.AlreadyMigrated -> migrated += evidenceId
                is RotationResult.Failed -> failed += evidenceId
            }
        }
        if (failed.isNotEmpty()) {
            return RotationRunReport(
                RotationTriggerOutcome.PARTIALLY_MIGRATED,
                trigger,
                migrated = migrated,
                failed = failed,
                detail = "${failed.size} of ${eligible.size} entries did not migrate; '$fromVersion' stays active",
            )
        }

        val remaining = try {
            scope.all().count { (_, metadata) -> metadata.keyVersion == fromVersion }
        } catch (error: Exception) {
            return RotationRunReport(
                RotationTriggerOutcome.RETIREMENT_BLOCKED,
                trigger,
                migrated = migrated,
                detail = "reference re-scan failed: ${error.message}",
            )
        }
        if (remaining > 0) {
            return RotationRunReport(
                RotationTriggerOutcome.RETIREMENT_BLOCKED,
                trigger,
                migrated = migrated,
                detail = "$remaining reference(s) still point at '$fromVersion'",
            )
        }

        return try {
            lifecycle.completeRotation(fromVersion, toVersion, now)
            RotationRunReport(RotationTriggerOutcome.ROTATED, trigger, migrated = migrated)
        } catch (error: Exception) {
            RotationRunReport(
                RotationTriggerOutcome.RETIREMENT_BLOCKED,
                trigger,
                migrated = migrated,
                detail = "lifecycle write failed: ${error.message}",
            )
        }
    }

    fun isDue(): Boolean {
        val snapshot = lifecycle.snapshot()
        return EvidenceKeyPolicy.rotationDue(snapshot.lastRotationEpochMs, nowMillis())
    }

    private fun blocked(trigger: RotationTrigger, detail: String) = RotationRunReport(
        RotationTriggerOutcome.RETIREMENT_BLOCKED,
        trigger,
        detail = detail,
    )
}
