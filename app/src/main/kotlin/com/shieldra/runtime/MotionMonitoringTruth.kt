package com.shieldra.runtime

/**
 * The only projection Motion monitoring may render from (correction (ب) of amendment A-18).
 *
 * Both inputs are read off the platform: whether the foreground service is actually running, and
 * whether the adapter has a live registration. Neither comes from the user pressing Start, and
 * neither comes from this package — a press that Android refused, a service killed with its process,
 * or an unregistered sensor must all render as no protection. This is Batch F's rule applied one
 * level up: [BackgroundExecutionController.monitoringClaim] cannot know whether a detector runs, and
 * a runtime object cannot know whether a service does.
 */
object MotionMonitoringTruth {
    fun claim(serviceRunning: Boolean, sensorReporting: Boolean): MonitoringClaim = when {
        !serviceRunning -> MonitoringClaim.NoExecution
        !sensorReporting -> MonitoringClaim.ExecutionWithoutDetector
        else -> MonitoringClaim.ActiveGuardMonitoring
    }
}
