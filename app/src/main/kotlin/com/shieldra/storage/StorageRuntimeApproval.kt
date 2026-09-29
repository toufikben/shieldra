package com.shieldra.storage

/**
 * §15 production-wiring gate (decision, not a feature flag).
 *
 * Local storage may only be attached to the running application behind this
 * explicit approval. It is `false` until the owner flips it in a visible
 * commit (§14: no silent policy change). Flipping this constant is the owner's
 * sign-off that the storage/Keystore path is cleared for runtime use.
 */
object StorageRuntimeApproval {
    const val approved: Boolean = false
}

/**
 * Outcome of the runtime storage-install decision. `Installed` carries the
 * opened storage; `RecoveryRequired` carries the diagnosis and deliberately
 * holds no storage, because a failed open must never be turned into a
 * partially usable database (§4.2–§4.4). `NotApproved` means the gate was off
 * and no open was attempted at all.
 */
sealed class StorageInstallDecision<out T> {
    object NotApproved : StorageInstallDecision<Nothing>()

    data class Installed<out T>(val storage: T) : StorageInstallDecision<T>()

    data class RecoveryRequired(
        val reason: StorageRecoveryReason,
        val detail: String,
    ) : StorageInstallDecision<Nothing>()
}

/**
 * Pure install decision, kept free of Application and Keystore so all three
 * outcomes are unit-testable. When the gate is off it does not even invoke
 * [open], so a default build provably touches no storage. When it is on, the
 * [open] result is mapped without any destructive fallback: Ready installs,
 * RecoveryRequired is surfaced as-is.
 */
fun <T> decideStorageInstall(
    approved: Boolean,
    open: () -> StorageOpenResult<T>,
): StorageInstallDecision<T> {
    if (!approved) return StorageInstallDecision.NotApproved
    return when (val result = open()) {
        is StorageOpenResult.Ready -> StorageInstallDecision.Installed(result.value)
        is StorageOpenResult.RecoveryRequired -> StorageInstallDecision.RecoveryRequired(result.reason, result.detail)
    }
}
