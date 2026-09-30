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
 * Whether this app has already put the system permission dialog in front of the
 * user (amendment A-6).
 *
 * Android answers `checkSelfPermission` with DENIED both for a refusal and for a
 * permission the user has never been asked about, so the app has to remember
 * that difference itself. This is settings-level capability state: it is not
 * written into any Security Event, and an Event's truth never depends on it.
 */
interface PermissionRequestRecord {
    fun hasBeenRequested(): Boolean

    fun markRequested()
}

/**
 * The preference-backed [PermissionRequestRecord].
 *
 * One boolean in app-private storage, deliberately not in the Room database: the
 * database holds security events and their truth, and this is neither.
 */
class PreferencesPermissionRequestRecord(context: Context) : PermissionRequestRecord {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasBeenRequested(): Boolean = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    override fun markRequested() {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME: String = "shieldra_notification"
        const val KEY_PERMISSION_REQUESTED: String = "permission_requested"
    }
}

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
    private val permissions: PermissionRequestRecord = PreferencesPermissionRequestRecord(context),
    private val channelId: String = DEFAULT_CHANNEL_ID,
) {
    /** The live platform answer, never a cached one. */
    fun permissionState(): NotificationPermissionState {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < NotificationPermissionState.RUNTIME_PERMISSION_FROM_SDK_INT) {
            return NotificationPermissionState.NotRequired
        }
        // Silence is not a refusal: before the dialog has been shown there is no
        // platform answer to report, so the state stays "not requested".
        val answer = if (permissions.hasBeenRequested()) isPermissionGranted() else null
        return NotificationPermissionState.forSdkInt(sdk, answer)
    }

    /**
     * Records that the system dialog has been answered. Called from the request
     * callback — allowing or refusing — and never when the button is pressed, so
     * an interrupted request is not presented as a refusal.
     */
    fun recordPermissionRequest() {
        permissions.markRequested()
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
     *
     * The event id is used as the notification tag, so a repeated Panic press on
     * the same incident updates that incident's own notification instead of
     * stacking a second one (A-6: no duplicated notification work), and two
     * different events can never overwrite each other by colliding on a hash.
     */
    fun postSecurityEvent(eventId: String, statusLabel: String): LocalPostResult =
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
                    context.getString(com.shieldra.app.R.string.notification_event_body, statusLabel),
                )
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(eventId, eventId.hashCode(), notification)
            LocalPostResult.PostedLocally
        }

    companion object {
        const val DEFAULT_CHANNEL_ID: String = "shieldra_security_events"
    }
}
