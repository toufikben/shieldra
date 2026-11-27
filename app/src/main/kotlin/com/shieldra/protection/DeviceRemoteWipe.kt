package com.shieldra.protection

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/**
 * The one place in the app that may erase the phone (P2-3, owner decision 2026-11-27).
 *
 * `wipeData` cannot be undone, so this file exists to be the single answer to "was this confirmed".
 * Every gate is asked here, at the moment of the erase, because the window can last ten minutes and
 * the owner can change any of them inside it:
 *
 * 1. the owner's explicit `remoteWipeEnabled` switch (never inherited from `remoteCommandsEnabled`),
 * 2. the plan (`TierGate.allowsRemoteWipe`),
 * 3. protection still armed,
 * 4. an active administrator that really holds the wipe policy — without it the platform refuses, and
 *    a refused erase reported as done would leave the owner believing a stolen phone is empty.
 *
 * The confirmation rule itself lives in [RemoteWipeRequest] and is pure, so the decision is unit-tested
 * without a device; this object only carries out the answer it gives.
 */
object DeviceRemoteWipe {

    private const val TAG = "DeviceRemoteWipe"

    /** What the erase attempt ended in. Reported to the owner as one sentence each. */
    sealed interface Outcome {
        /** The platform accepted the wipe. The phone is going down. */
        data object Erased : Outcome

        /** A gate is not in place now, so nothing was erased. */
        data class Refused(val reason: String) : Outcome

        /** The platform refused the call; the phone is intact. */
        data class Failed(val reason: String) : Outcome
    }

    /**
     * Reads the gates as they are now. Separate from the erase so the poller can answer honestly
     * without attempting anything, and so a test can state what "all gates open" means.
     */
    fun gatesOpen(context: Context): Boolean {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        return settings.armed &&
            settings.remoteWipeEnabled &&
            com.shieldra.billing.TierGate.allowsRemoteWipe(settings.tier) &&
            DeviceRemoteWipePower.isGranted(app)
    }

    /**
     * Carries out a confirmation that [RemoteWipeRequest] already accepted. The rule is asked again
     * here with the gates read at this instant, so a caller cannot skip them by passing a stale answer.
     */
    fun eraseIfConfirmed(context: Context, nowMs: Long = System.currentTimeMillis()): Outcome {
        val app = context.applicationContext
        val open = RemoteWipeStore(app).read()
        val decision = RemoteWipeRequest.decide(
            argument = RemoteWipeRequest.CONFIRM_ARGUMENT,
            open = open,
            nowMs = nowMs,
            gatesOpen = gatesOpen(app),
        )
        return when (decision) {
            RemoteWipeRequest.Decision.Erase -> erase(app)
            RemoteWipeRequest.Decision.GatesClosed -> Outcome.Refused("a protection gate is not in place")
            RemoteWipeRequest.Decision.Expired -> Outcome.Refused("the confirmation window has passed")
            RemoteWipeRequest.Decision.NothingWaiting -> Outcome.Refused("no wipe request is waiting")
            else -> Outcome.Refused("the request is not a confirmation")
        }
    }

    private fun erase(app: Context): Outcome = runCatching {
        val manager = app.getSystemService(DevicePolicyManager::class.java)
            ?: return@runCatching Outcome.Refused("the device administrator is not available")
        val admin = LockGuardAdmin.component(app)
        if (!manager.isAdminActive(admin) ||
            !DeviceRemoteWipePower.isGranted(app)
        ) {
            return@runCatching Outcome.Refused("the device administrator does not hold the wipe power")
        }
        // The window is spent the moment the erase is handed over: a second confirmation that races
        // this one must find nothing waiting rather than erase twice.
        RemoteWipeStore(app).clear()
        manager.wipeData(0)
        Log.w(TAG, "the owner's remote wipe was accepted by the platform")
        Outcome.Erased
    }.getOrElse { error ->
        Log.e(TAG, "the remote wipe was refused by the platform: ${error.message}", error)
        Outcome.Failed(error.message ?: "the platform refused the erase")
    }
}
