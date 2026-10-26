package com.shieldra.protection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/** What the phone's data link really is right now; [UNKNOWN] only when the platform would not say. */
enum class LinkKind { WIFI, CELLULAR, OTHER, OFFLINE, UNKNOWN }

/** One real reading of the phone's own state, for the dashboard's status strip (amendment A-38). */
data class DeviceSnapshot(
    val batteryPercent: Int?,
    val link: LinkKind,
    /** Seconds since a protection guard last really ran a check; null if none has run yet. */
    val lastCheckSeconds: Long?,
)

object DeviceStatus {

    fun snapshot(context: Context, nowMs: Long = System.currentTimeMillis()): DeviceSnapshot {
        val app = context.applicationContext
        return DeviceSnapshot(
            batteryPercent = batteryPercent(app),
            link = link(app),
            lastCheckSeconds = LastCheck.secondsSince(ProtectionSettingsStore(app).lastCheckEpochMs(), nowMs),
        )
    }

    fun batteryPercent(context: Context): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..100 }

    fun link(context: Context): LinkKind {
        val cm = runCatching { context.getSystemService(ConnectivityManager::class.java) }.getOrNull()
            ?: return LinkKind.UNKNOWN
        val caps = runCatching { cm.activeNetwork?.let(cm::getNetworkCapabilities) }.getOrNull()
            ?: return LinkKind.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LinkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkKind.CELLULAR
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> LinkKind.OTHER
            else -> LinkKind.OFFLINE
        }
    }
}

/** Pure arithmetic for the "last check" age, unit-tested without a device. */
object LastCheck {
    /** Whole seconds between [lastEpochMs] and [nowMs]; null when never checked or the clock went back. */
    fun secondsSince(lastEpochMs: Long, nowMs: Long): Long? {
        if (lastEpochMs <= 0L || nowMs < lastEpochMs) return null
        return (nowMs - lastEpochMs) / 1000
    }
}
