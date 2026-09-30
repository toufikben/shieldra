package com.shieldra.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The real Android side of a local event notification (amendment A-6).
 *
 * Scope is deliberately one local channel and one post. There is no push service,
 * no cloud, no SMS, no email, and no external provider: §30 keeps those out of
 * this batch, and SHIELDRA claims nothing it cannot prove here.
 *
 * Uses the platform [Notification.Builder] rather than `NotificationCompat`:
 * the channel constructor has existed since API 26 and this app's `minSdk` is
 * 26, so no extra dependency is justified for one notification.
 */
class LocalEventNotifier(
    private val context: Context,
    private val channelId: String = DEFAULT_CHANNEL_ID,
) {
    /** The live platform answer, never a cached one. */
    fun permissionState(): NotificationPermissionState {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < NotificationPermissionState.RUNTIME_PERMISSION_FROM_SDK_INT) {
            return NotificationPermissionState.NotRequired
        }
        return NotificationPermissionState.forSdkInt(sdk, isPermissionGranted())
    }

    private fun isPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Creates the channel if the platform needs one. A notification posted to a
     * missing channel is dropped silently, which would look like a success the
     * user never saw, so this runs before the first post.
     */
    fun ensureChannel(): Boolean = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(com.shieldra.app.R.string.notification_channel_security_events),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(
                        com.shieldra.app.R.string.notification_channel_security_events_description,
                    )
                },
            )
        }
        true
    } catch (error: Exception) {
        false
    }

    /**
     * Posts one local notification for a real recorded event, after re-checking
     * the permission. Returns what actually happened — never a "delivered" claim.
     */
    fun postSecurityEvent(eventId: String, occurredAtLabel: String): LocalPostResult =
        permissionState().postIfPossible(
            sdkInt = Build.VERSION.SDK_INT,
            actuallyGranted = isPermissionGranted(),
        ) {
            if (!ensureChannel()) {
                return@postIfPossible LocalPostResult.FailedToPost("notification channel could not be created")
            }
            val notification = Notification.Builder(context, channelId)
                .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
                .setContentTitle(context.getString(com.shieldra.app.R.string.notification_event_title))
                .setContentText(
                    context.getString(com.shieldra.app.R.string.notification_event_body, occurredAtLabel),
                )
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(eventId.hashCode(), notification)
            LocalPostResult.PostedLocally
        }

    companion object {
        const val DEFAULT_CHANNEL_ID: String = "shieldra_security_events"
    }
}
