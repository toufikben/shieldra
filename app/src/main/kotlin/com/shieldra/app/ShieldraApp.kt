package com.shieldra.app

import android.app.Application
import com.shieldra.storage.LocalShieldraStorage
import com.shieldra.storage.RetentionSweepReport
import com.shieldra.storage.RotationRunReport
import com.shieldra.storage.StorageInstallDecision
import com.shieldra.storage.StorageRuntimeApproval
import com.shieldra.storage.decideStorageInstall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the startup maintenance thread has learned so far.
 *
 * The thread runs off `onCreate` because the retention sweep and the rotation check are Room reads and
 * writes, so neither outcome exists when the first screen is composed. They used to be written to plain
 * fields that a screen read once — at exactly the moment the fields were guaranteed to still be empty —
 * so the storage screen reported "not finished on this run yet" for the rest of the process, even over
 * a rotation that had already failed. Publishing a value the screen collects is what makes the report
 * arrive after it exists.
 *
 * Each step publishes on its own: a rotation that takes a minute must not hide the sweep that already
 * finished, and a sweep that threw must not be hidden by a rotation that succeeded.
 */
data class StartupMaintenance(
    /** The expiry sweep's outcome, or null until it finishes. */
    val retentionReport: RetentionSweepReport? = null,
    /** A sweep that could not read the stored set; the clock behind it is then unknown. */
    val retentionFailure: Throwable? = null,
    /** The rotation check's outcome, or null until it finishes. */
    val rotationReport: RotationRunReport? = null,
    /** A rotation check that could not even read the key lifecycle record. */
    val rotationFailure: Throwable? = null,
) {
    /** True until both steps have reported, one way or the other. */
    val running: Boolean
        get() = retentionReport == null && retentionFailure == null &&
            rotationReport == null && rotationFailure == null
}

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

    /** The Google Play Pro subscription (A-40); started once the first activity is up. */
    val proStore: com.shieldra.billing.PlayProStore by lazy { com.shieldra.billing.PlayProStore(this) }

    private val maintenanceState = MutableStateFlow(StartupMaintenance())

    /**
     * The startup sweep and rotation, collectable so a screen shows the outcome when it exists instead
     * of the empty value it had while the thread was still running.
     */
    val startupMaintenance: StateFlow<StartupMaintenance> = maintenanceState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        val decision = decideStorageInstall(
            approved = StorageRuntimeApproval.approved,
            // The retention windows are the owner's setting, read once at open time: the sweeper is
            // built with them, so changing the setting changes the next process's expiry, never a
            // half-applied window inside the running one.
            open = {
                LocalShieldraStorage.openOrRecovery(
                    context = applicationContext,
                    retentionDays = {
                        com.shieldra.protection.ProtectionSettingsStore(applicationContext)
                            .load()
                            .retentionDays
                    },
                )
            },
        )
        storageDecision = decision
        if (decision is StorageInstallDecision.Installed) {
            installedStorage = decision.storage
            checkEvidenceRotation(decision.storage)
            // A-32: alerts kept while offline go out as soon as a network is usable again.
            com.shieldra.delivery.AlertRuntime.watchNetwork(this)
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
            // Q-K-2's first authorized execution point, run before the rotation
            // check and on this same thread: expiry is judged against the stored
            // ages, and a rotation rewrites payloads. Sweeping afterwards would
            // read freshly written files as new.
            try {
                val sweep = storage.sweepExpiredEvidence()
                maintenanceState.value = maintenanceState.value.copy(retentionReport = sweep, retentionFailure = null)
            } catch (error: Exception) {
                maintenanceState.value = maintenanceState.value.copy(retentionFailure = error)
            }
            try {
                val rotated = storage.rotateEvidenceIfNeeded(manual = false)
                maintenanceState.value = maintenanceState.value.copy(rotationReport = rotated, rotationFailure = null)
            } catch (error: Exception) {
                maintenanceState.value = maintenanceState.value.copy(rotationFailure = error)
            }
        }.start()
    }
}
