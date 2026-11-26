package com.shieldra.protection

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * The charging question the safe-zone policy asks (A-45 D2): a phone that lies still on a night stand
 * plugged in is where it sleeps, and the owner chose not to be woken for it.
 *
 * It reads the sticky `ACTION_BATTERY_CHANGED` broadcast instead of a `BatteryManager.isCharging` call:
 * that accessor needs API 31 and this app still ships to API 26, and the policy path must not carry a
 * version branch. One reader, one line, so the responder never registers a receiver of its own for a
 * fact the system already publishes.
 */
object BatteryLevel {

    fun isCharging(context: Context): Boolean {
        val status = runCatching {
            context.applicationContext
                .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        }.getOrNull()
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * The charge in whole percent, or null when the system has no answer. Read from the same sticky
     * broadcast for the same reason as [isCharging]; the snatch ladder uses it to honour the 10%
     * agreement (A-46) without waking the battery manager.
     */
    fun percent(context: Context): Int? = runCatching {
        val status = context.applicationContext
            .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@runCatching null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return@runCatching null
        (level * 100 / scale).coerceIn(0, 100)
    }.getOrNull()
}
