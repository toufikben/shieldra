package com.shieldra.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.shieldra.detection.MotionMonitoringService
import com.shieldra.notification.LocalEventNotifier
import com.shieldra.notification.NotificationPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a running Motion monitor publishes about itself (amendment A-18, correction (ب)).
 *
 * Both facts are read off the platform by the object that holds them — the service's execution phase
 * and its adapter's live registration. Neither is derived from the user pressing Start, which is the
 * difference between a request and a state.
 */
interface MotionMonitor {
    val executionRunning: Boolean
    val sensorReporting: Boolean
}

/**
 * The app-process handle for Motion monitoring: where the UI reads its claim from, and how a start
 * or a stop reaches the service.
 *
 * It owns no sensor adapter. The service is that adapter's only owner (A-18 correction (أ)), so
 * `isReporting` cannot be held twice and unregistered twice — this object only asks a live service
 * what its own state is. While no service has published itself the claim is
 * [MonitoringClaim.NoExecution], whatever the user pressed.
 */
object MotionRuntime {
    /** Written on the main thread by the service, read from the watchdog and any reader thread. */
    @Volatile
    private var monitor: MotionMonitor? = null
    private val claims = MutableStateFlow(claimOf(null))

    /** The only Flow a screen may render a Motion state from. */
    val state: StateFlow<MonitoringClaim> = claims.asStateFlow()

    /** The same claim, read once. */
    fun claim(): MonitoringClaim = claimOf(monitor)

    /**
     * Brings monitoring up when the platform facts allow it and returns the decision the UI has to
     * report. [storageInstalled] is checked here rather than left to the service because monitoring
     * that could not record what it confirms would be a claim SHIELDRA cannot deliver.
     */
    fun startMonitoring(context: Context, storageInstalled: Boolean): MotionMonitoringStartDecision {
        val decision = if (!storageInstalled) {
            MotionMonitoringStartDecision.DoNotStartMonitoring(NO_STORAGE_REASON)
        } else {
            MotionMonitoringStartGate.decide(
                sdkInt = Build.VERSION.SDK_INT,
                notificationGranted = LocalEventNotifier(context).permissionState().platformAnswer(),
            )
        }
        if (decision is MotionMonitoringStartDecision.MayStartMonitoring) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MotionMonitoringService::class.java),
                )
            } catch (refused: IllegalStateException) {
                // Android 12+ refuses a foreground-service start from the background
                // (ForegroundServiceStartNotAllowedException is an IllegalStateException). That is a
                // refusal to report, never a crash and never a claim that monitoring started.
                return MotionMonitoringStartDecision.DoNotStartMonitoring(
                    refused.message ?: "Android did not allow monitoring to start right now.",
                )
            }
        }
        return decision
    }

    /**
     * Takes monitoring down. The service still has to tear the registration down itself, so the
     * claim only changes when it says it has.
     */
    fun stopMonitoring(context: Context) {
        context.stopService(Intent(context, MotionMonitoringService::class.java))
    }

    /** Called by the service once it has really come up. */
    fun publish(published: MotionMonitor) {
        monitor = published
        refresh()
    }

    /** Called by the service as it is torn down; a publication must not outlive its service. */
    fun retract(retracted: MotionMonitor) {
        if (monitor === retracted) monitor = null
        refresh()
    }

    /** Re-reads the published monitor after a transition only the monitor can see. */
    fun refresh() {
        claims.value = claimOf(monitor)
    }

    private fun claimOf(monitor: MotionMonitor?): MonitoringClaim = MotionMonitoringTruth.claim(
        serviceRunning = monitor?.executionRunning == true,
        sensorReporting = monitor?.sensorReporting == true,
    )

    /**
     * The gate's input is the platform's answer, and `NotRequired`/`NotRequested` are exactly the
     * cases with no answer to report: a version with no such permission to grant, and a permission
     * nobody has been asked about (A-6).
     */
    private fun NotificationPermissionState.platformAnswer(): Boolean? = when (this) {
        NotificationPermissionState.Granted -> true
        NotificationPermissionState.Denied -> false
        NotificationPermissionState.NotRequired, NotificationPermissionState.NotRequested -> null
    }

    private const val NO_STORAGE_REASON: String =
        "SHIELDRA monitoring records what it confirms, and this build has no installed storage to " +
            "record it in, so monitoring is not started."
}
