package com.shieldra.app.presentation.guard

/**
 * A single 0–100 "protection strength" read, computed only from facts the app can verify — the
 * guards the owner turned on, the runtime permissions actually granted, battery exemption, the
 * device-admin role the Lock guard needs, and whether any alert channel is live. It is honest
 * projection, not a flattering number: a disarmed phone scores 0 because no protection is running,
 * whatever the stored toggles say, and the Lock guard earns its points only when the owner both
 * switched it on **and** granted the role that lets it hear wrong unlock attempts.
 *
 * P2-5: a stored switch is not a running guard. The SIM and battery points used to come from the
 * saved settings alone, so a phone whose protection service had been killed by the battery manager
 * still showed those guards as protecting — the exact silent failure the watchdog exists to report.
 * [guardsRunning] is the live fact the service publishes about itself (`ProtectionService.live`,
 * collected by the dashboard rather than read once), and while it is false those two guards earn
 * nothing. The remaining inputs are platform answers
 * (a granted permission, an exemption, a live channel), which a stopped service does not withdraw.
 *
 * Pure: facts in, score out. Unit-tested without a device.
 */
object ProtectionScore {

    data class Inputs(
        val armed: Boolean,
        /** True only while the protection service is really up; the two guard weights need it. */
        val guardsRunning: Boolean,
        /** The Lock guard: a switch without the device-admin role protects nothing. */
        val lockEnabled: Boolean,
        val motionMonitoring: Boolean,
        val simEnabled: Boolean,
        val batteryEnabled: Boolean,
        val cameraGranted: Boolean,
        val locationGranted: Boolean,
        val notificationsReady: Boolean,
        val deviceAdminActive: Boolean,
        val batteryExempt: Boolean,
        val anyChannelActive: Boolean,
    )

    /** Weighted points; they sum to exactly 100 when everything is on and granted. */
    fun score(i: Inputs): Int {
        if (!i.armed) return 0
        var s = 0
        // One capability, one weight: a switch without the role protects nothing.
        if (i.lockEnabled && i.deviceAdminActive) s += W_LOCK
        if (i.motionMonitoring) s += W_MOTION
        // A guard the owner switched on, inside a service that is really running.
        if (i.guardsRunning && i.simEnabled) s += W_SIM
        if (i.guardsRunning && i.batteryEnabled) s += W_BATTERY_GUARD
        if (i.anyChannelActive) s += W_CHANNEL
        if (i.cameraGranted) s += W_CAMERA
        if (i.locationGranted) s += W_LOCATION
        if (i.notificationsReady) s += W_NOTIFICATIONS
        if (i.batteryExempt) s += W_BATTERY_EXEMPT
        return s.coerceIn(0, 100)
    }

    const val W_LOCK = 19
    const val W_MOTION = 10
    const val W_SIM = 10
    const val W_BATTERY_GUARD = 8
    const val W_CHANNEL = 25
    const val W_CAMERA = 10
    const val W_LOCATION = 10
    const val W_NOTIFICATIONS = 5
    const val W_BATTERY_EXEMPT = 3
}
