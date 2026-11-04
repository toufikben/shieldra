package com.shieldra.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import java.util.Locale

/**
 * The notice the holder of a locked phone sees (A-45 D1 and D3). A public, ongoing notification with a
 * live timer: since Android 14 a full-screen takeover is refused to apps that are not calling or alarm
 * apps, and this app is neither. It says what the phone is going to do and in how long, in the three
 * languages the app ships, because the app cannot know who is reading it.
 *
 * It is deliberately separate from [LostNotice]: that one is the owner's own finder message, turned on
 * from Telegram. This one is the consequence of a confirmed movement, and it is the thing the owner
 * asked for — a phone that speaks before it starts sending pictures.
 */
object TheftDemand {

    private const val CHANNEL_ID = "shieldra_theft_demand"

    /** Distinct from [LostNotice]'s 20020 so both can be on screen at once and be cancelled apart. */
    const val NOTIFICATION_ID = 20_021

    /** The notice carries all three copies at once, in this order. */
    private val LOCALES = listOf("ar", "en", "fr")

    fun show(context: Context, contact: String, ownerMessage: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    app.getString(com.shieldra.app.R.string.theft_demand_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val localized = LOCALES.map { app.forLocale(it) }
        val body = TheftNoticeText.compose(
            arabic = localized[0].getString(com.shieldra.app.R.string.theft_demand_body),
            english = localized[1].getString(com.shieldra.app.R.string.theft_demand_body),
            french = localized[2].getString(com.shieldra.app.R.string.theft_demand_body),
            ownerMessage = ownerMessage.trim().takeIf(String::isNotEmpty)?.let { own ->
                app.getString(com.shieldra.app.R.string.theft_demand_owner_message, own)
            },
            contactLine = contact.trim().takeIf(String::isNotEmpty)?.let {
                app.getString(com.shieldra.app.R.string.theft_demand_contact, OwnerContact.normalized(it))
            },
        )
        val notice = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(app.getString(com.shieldra.app.R.string.theft_demand_title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            // A base in the future is how a notification counts down: this is the demand window.
            .setWhen(System.currentTimeMillis() + SnatchLadder.DEMAND_MS)
            .apply {
                OwnerContact.dialUri(contact)?.let { uri ->
                    addAction(
                        Notification.Action.Builder(
                            com.shieldra.app.R.drawable.ic_shield_notification,
                            app.getString(com.shieldra.app.R.string.theft_demand_call),
                            PendingIntent.getActivity(
                                app,
                                0,
                                Intent(Intent.ACTION_DIAL, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        ).build(),
                    )
                }
            }
            .build()
        manager.notify(NOTIFICATION_ID, notice)
    }

    fun hide(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    /**
     * The same resources read in one fixed locale. The app has its own language override, but the
     * notice is meant for a stranger who never opened that setting, so each copy is pulled directly
     * from its locale rather than from whatever the owner chose.
     */
    private fun Context.forLocale(tag: String): Context {
        @Suppress("DEPRECATION")
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(configuration)
    }
}
