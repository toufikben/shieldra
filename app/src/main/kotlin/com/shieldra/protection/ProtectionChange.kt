package com.shieldra.protection

/**
 * Which settings changes weaken protection. Whoever holds an unlocked phone must not be able to
 * switch a guard off, or send alerts somewhere else, without the owner's screen lock, so the UI asks
 * for it exactly when this says a change makes the phone less protected.
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
            new.lockThreshold > old.lockThreshold ||
            new.motionResponse != old.motionResponse && new.motionResponse == ProtectionSettings.MotionResponse.SILENT
}
