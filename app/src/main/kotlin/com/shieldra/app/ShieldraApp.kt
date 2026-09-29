package com.shieldra.app

import android.app.Application
import com.shieldra.storage.LocalShieldraStorage
import com.shieldra.storage.StorageInstallDecision
import com.shieldra.storage.StorageRuntimeApproval
import com.shieldra.storage.decideStorageInstall

/**
 * §15 production wiring, behind an explicit runtime approval gate.
 *
 * Local storage is installed only when [StorageRuntimeApproval.approved] is
 * true, and the owner flips that constant in a visible commit (§14). With the
 * gate off (the default), no open is attempted at all, so a default build
 * provably creates no database and touches no Keystore key — the Phase-3
 * no-side-effect behavior is preserved.
 *
 * Opening goes exclusively through [LocalShieldraStorage.openOrRecovery],
 * never the raw factory: a failure surfaces as RecoveryRequired and is kept as
 * state rather than crashing or falling back to a destructive recreate
 * (§4.2–§4.4). This wiring does not by itself close §15 or G4.
 */
class ShieldraApp : Application() {
    var storageDecision: StorageInstallDecision<LocalShieldraStorage> = StorageInstallDecision.NotApproved
        private set

    var installedStorage: LocalShieldraStorage? = null
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
        }
    }
}
