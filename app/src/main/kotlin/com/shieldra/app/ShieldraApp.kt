package com.shieldra.app

import android.app.Application
import com.shieldra.storage.LocalShieldraStorage
import com.shieldra.storage.RotationRunReport
import com.shieldra.storage.StorageInstallDecision
import com.shieldra.storage.StorageRuntimeApproval
import com.shieldra.storage.decideStorageInstall

/**
 * §15 production wiring, behind an explicit runtime approval gate.
 *
 * Local storage is installed only when [StorageRuntimeApproval.approved] is
 * true. The owner flipped that constant to true in a visible commit
 * (amendment A-5, §14) after the first detector's device verification passed
 * (Q-C2 option 1), so the approved build now opens storage at process start.
 * A build that is behind an unflipped gate still provably creates no database
 * and touches no Keystore key.
 *
 * Opening goes exclusively through [LocalShieldraStorage.openOrRecovery],
 * never the raw factory: a failure surfaces as RecoveryRequired and is kept as
 * state rather than crashing or falling back to a destructive recreate
 * (§4.2–§4.4). Installing storage does not by itself close §15 or G4, and it
 * never claims protection: with no detector running, the honest projection
 * stays idle/attention, never "Protected".
 */
class ShieldraApp : Application() {
    var storageDecision: StorageInstallDecision<LocalShieldraStorage> = StorageInstallDecision.NotApproved
        private set

    var installedStorage: LocalShieldraStorage? = null
        private set

    /** Outcome of the rotation check this process started; null until it completes. */
    var lastRotationReport: RotationRunReport? = null
        private set

    /**
     * A rotation check that could not even read the key lifecycle record. Kept
     * instead of swallowed: the 90-day clock is then unknown, and guessing a key
     * version is exactly what §6 forbids.
     */
    var lastRotationFailure: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val decision = decideStorageInstall(
            approved = StorageRuntimeApproval.approved,
            open = { LocalShieldraStorage.openOrRecovery(applicationContext) },
        )
        storageDecision = decision
        if (decision is StorageInstallDecision.Installed) {
            installedStorage = decision.storage
            checkEvidenceRotation(decision.storage)
        }
    }

    /**
     * The approved rotation trigger (protocol §9): one interval check per process
     * start, off the main thread because the eligibility scan is a Room read and a
     * migration is a Room write.
     *
     * Since the owner flipped the gate (A-5), an approved process start runs this
     * scan over real storage. An unflipped build never reaches here: no storage
     * to scan, no lifecycle record, no Keystore access.
     */
    private fun checkEvidenceRotation(storage: LocalShieldraStorage) {
        Thread {
            try {
                lastRotationReport = storage.rotateEvidenceIfNeeded(manual = false)
            } catch (error: Exception) {
                lastRotationFailure = error
            }
        }.start()
    }
}
