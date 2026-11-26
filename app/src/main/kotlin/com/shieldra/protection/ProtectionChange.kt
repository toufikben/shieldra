package com.shieldra.protection

/**
 * Which settings changes weaken protection. Whoever holds an unlocked phone must not be able to
 * switch a guard off, or send alerts somewhere else, without the owner's screen lock, so the UI asks
 * for it exactly when this says a change makes the phone less protected.
 *
 * The list is about effect, not about switch position. Every case below leaves some guard nominally
 * "on" while making it do less: a shorter clip, a wider threshold, a lazier interval, a later
 * last-breath capture, a plan that takes capabilities away. A row that is not a weakening (a longer
 * clip, a tighter interval, a higher battery level, an added contact) asks for nothing, because a
 * prompt for every edit trains the owner to approve without reading.
 */
object ProtectionChange {

    fun weakens(old: ProtectionSettings, new: ProtectionSettings): Boolean =
        (old.armed && !new.armed) ||
            (old.lockEnabled && !new.lockEnabled) ||
            (old.snatchLockEnabled && !new.snatchLockEnabled) ||
            (old.simEnabled && !new.simEnabled) ||
            (old.batteryEnabled && !new.batteryEnabled) ||
            (old.countdownEnabled && !new.countdownEnabled) ||
            (old.remoteCommandsEnabled && !new.remoteCommandsEnabled) ||
            (old.liveTrackingEnabled && !new.liveTrackingEnabled) ||
            (old.dualCameraEnabled && !new.dualCameraEnabled) ||
            (old.audioEnabled && !new.audioEnabled) ||
            (old.escalationEnabled && !new.escalationEnabled) ||
            (old.geofenceEnabled && !new.geofenceEnabled) ||
            (old.motionEnabled && !new.motionEnabled) ||
            new.motionResponse != old.motionResponse && new.motionResponse == ProtectionSettings.MotionResponse.SILENT ||
            // Lock guard: a higher threshold waits for more wrong attempts before the alarm.
            new.lockThreshold > old.lockThreshold ||
            // Evidence kept per capture: a shorter clip is less proof of what happened.
            new.audioSeconds < old.audioSeconds ||
            // Escalation: a wider threshold waits for more intrusions before the siren.
            new.escalationThreshold > old.escalationThreshold ||
            // Live trail: a lazier interval or a shorter run leaves more of the journey unrecorded.
            new.trackingIntervalMinutes > old.trackingIntervalMinutes ||
            new.trackingDurationMinutes < old.trackingDurationMinutes ||
            // Last breath: a lower level announces the dying battery later, sometimes too late.
            new.batteryThreshold < old.batteryThreshold ||
            // Motion response: a longer grace is a longer window to act unrecorded.
            new.motionGraceSeconds > old.motionGraceSeconds ||
            // Safe zones: a lazier sampler notices leaving a zone late.
            new.geofenceIntervalMinutes > old.geofenceIntervalMinutes ||
            // Recovery: with no contact the lost notice has nobody to answer to.
            (old.ownerContact.isNotBlank() && new.ownerContact.isBlank()) ||
            // Plan: free takes photos, audio, Telegram, the countdown and tracking away.
            (old.tier != new.tier && new.tier == com.shieldra.billing.Tier.FREE)
}
