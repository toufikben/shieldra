package com.shieldra.protection

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Reads the current SIM/network state and classifies it against the owner's armed baseline using
 * [SimGuardLogic] (A-33). The baseline is captured the first time protection arms with a SIM in.
 * This only reads which subscriptions are present and whether there is service — never messages,
 * numbers of calls, or any content.
 */
class SimGuard(private val context: Context) {
    private val store = ProtectionSettingsStore(context)

    /** Captures the armed baseline if none is stored yet. Called when protection arms. */
    fun captureBaselineIfNeeded() {
        if (store.simBaseline().isNotEmpty()) return
        val ids = currentSimIds()
        if (ids.isNotEmpty()) store.setSimBaseline(ids)
    }

    /** Classifies the current state against the baseline, or null if the guard cannot read it. */
    fun check(): SimGuardLogic.Verdict? {
        if (!hasPhoneState()) return null
        val baseline = store.simBaseline()
        if (baseline.isEmpty()) return null
        return SimGuardLogic(baseline).classify(
            SimSnapshot(
                simIds = currentSimIds(),
                airplaneOn = airplaneOn(),
                hasService = hasService(),
            ),
        )
    }

    private fun currentSimIds(): Set<String> {
        if (!hasPhoneState()) return emptySet()
        val manager = ContextCompat.getSystemService(context, SubscriptionManager::class.java) ?: return emptySet()
        return runCatching {
            manager.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId.toString() }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun airplaneOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun hasService(): Boolean {
        val telephony = ContextCompat.getSystemService(context, TelephonyManager::class.java) ?: return false
        return runCatching { telephony.simState == TelephonyManager.SIM_STATE_READY }.getOrDefault(false)
    }

    private fun hasPhoneState(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
}
