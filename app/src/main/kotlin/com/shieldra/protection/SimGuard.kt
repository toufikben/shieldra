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

    /**
     * Establishes the armed baseline at the moment protection arms: the cards actually in the phone
     * now (audit §5.11), because arming is the owner saying "watch for anything other than these".
     *
     * Without this, a baseline captured months ago with a different card turns every honest re-arm into
     * a permanent "foreign SIM inserted" alarm — and an alarm that always fires is one nobody believes.
     * When the permission has not been granted nothing is readable at all, so nothing is written:
     * keeping the baseline that was taken honestly beats storing an empty one that claims the phone is
     * card-less. A readable, card-less phone does get an empty baseline, and that is the truthful
     * answer: a card that shows up afterwards is a foreign one.
     */
    fun rebaseline() {
        if (!hasPhoneState()) return
        store.setSimBaseline(currentSimIds())
    }

    /** Captures the armed baseline if none was ever stored. Called when protection starts running. */
    fun captureBaselineIfNeeded() {
        if (store.hasSimBaseline()) return
        if (!hasPhoneState()) return
        store.setSimBaseline(currentSimIds())
    }

    /** Classifies the current state against the baseline, or null if the guard cannot read it. */
    fun check(): SimGuardLogic.Verdict? {
        if (!store.hasSimBaseline()) return null
        val airplane = airplaneOn()
        if (!hasPhoneState()) {
            // Airplane mode costs no permission to read, and cutting the network is the first thing a
            // thief does. Without the owner's grant the SIM slots stay dark, but that free signal is
            // still reported rather than the whole guard going silent (audit §5.11).
            return if (airplane) SimGuardLogic.Verdict.NetworkLost else null
        }
        return SimGuardLogic(store.simBaseline()).classify(
            SimSnapshot(
                simIds = currentSimIds(),
                airplaneOn = airplane,
                hasService = hasService(),
            ),
        )
    }

    @android.annotation.SuppressLint("MissingPermission") // guarded by hasPhoneState() on the line below.
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
