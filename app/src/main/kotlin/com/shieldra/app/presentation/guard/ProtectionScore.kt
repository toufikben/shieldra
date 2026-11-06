package com.shieldra.app.presentation.guard

/**
 * A single 0–100 "protection strength" read, computed only from facts the app can verify — the
 * guards the owner turned on, the runtime permissions actually granted, battery exemption, and
 * whether any alert channel is live. It is honest projection, not a flattering number: a disarmed
 * phone scores 0 because no protection is running, whatever the stored toggles say.
 *
 * Design B (2026-11-06): the app holds no device administrator, so the former Lock guard and the
 * admin privilege are gone from the model; their weight moved to the guards that still run.
 *
 * Pure: facts in, score out. Unit-tested without a device.
 */
object ProtectionScore {

    data class Inputs(
        val armed: Boolean,
        val motionMonitoring: Boolean,
        val simEnabled: Boolean,
        val batteryEnabled: Boolean,
        val cameraGranted: Boolean,
        val locationGranted: Boolean,
        val notificationsReady: Boolean,
        val batteryExempt: Boolean,
        val anyChannelActive: Boolean,
    )

    /** Weighted points; they sum to exactly 100 when everything is on and granted. */
    fun score(i: Inputs): Int {
        if (!i.armed) return 0
        var s = 0
        if (i.motionMonitoring) s += W_MOTION
        if (i.simEnabled) s += W_SIM
        if (i.batteryEnabled) s += W_BATTERY_GUARD
        if (i.anyChannelActive) s += W_CHANNEL
        if (i.cameraGranted) s += W_CAMERA
        if (i.locationGranted) s += W_LOCATION
        if (i.notificationsReady) s += W_NOTIFICATIONS
        if (i.batteryExempt) s += W_BATTERY_EXEMPT
        return s.coerceIn(0, 100)
    }

    const val W_MOTION = 15
    const val W_SIM = 18
    const val W_BATTERY_GUARD = 12
    const val W_CHANNEL = 25
    const val W_CAMERA = 10
    const val W_LOCATION = 10
    const val W_NOTIFICATIONS = 5
    const val W_BATTERY_EXEMPT = 5
}
