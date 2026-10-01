package com.shieldra.runtime

import com.shieldra.notification.NotificationPermissionState

/**
 * Q-G5-b (amendment A-18): continuous Motion monitoring is a service the user must be able to see,
 * so it does not start when its ongoing notification would not reach them.
 *
 * Android keeps a foreground service running after `POST_NOTIFICATIONS` is refused; what it withholds
 * is the notification itself. A-15 conditions monitoring on a visible, accurate ongoing notice, so
 * the refusal here is SHIELDRA's product promise, not a claim about what the platform permits. Panic
 * is untouched: refusing to monitor never refuses to record an event (A-6).
 */
sealed interface MotionMonitoringStartDecision {
    /** The notification would be visible, so monitoring may be brought up. */
    data object MayStartMonitoring : MotionMonitoringStartDecision

    /** The permission exists and nobody has answered: ask, then start if it is granted. */
    data object AskForNotificationPermission : MotionMonitoringStartDecision

    /**
     * Monitoring stays down and [reason] is shown to the user. Today there are two honest reasons for
     * it: the notification permission was refused, so the ongoing notice that makes monitoring
     * visible would not reach them, and this build has no installed storage, so there would be
     * nothing to record a confirmed Movement in.
     */
    data class DoNotStartMonitoring(val reason: String) : MotionMonitoringStartDecision
}

object MotionMonitoringStartGate {
    private const val NOT_VISIBLE_REASON =
        "SHIELDRA monitoring has to stay visible to you as an ongoing notification, and without the " +
            "notification permission that notice would not reach you, so monitoring is not started."

    /**
     * Decides from the live platform answer, never from a cached one: [notificationGranted] is null
     * while nobody has asked, and a caller that re-reads the grant in Settings passes the real flag.
     */
    fun decide(sdkInt: Int, notificationGranted: Boolean?): MotionMonitoringStartDecision =
        when (NotificationPermissionState.forSdkInt(sdkInt, notificationGranted)) {
            NotificationPermissionState.NotRequired, NotificationPermissionState.Granted ->
                MotionMonitoringStartDecision.MayStartMonitoring

            NotificationPermissionState.NotRequested ->
                MotionMonitoringStartDecision.AskForNotificationPermission

            NotificationPermissionState.Denied ->
                MotionMonitoringStartDecision.DoNotStartMonitoring(NOT_VISIBLE_REASON)
        }
}
