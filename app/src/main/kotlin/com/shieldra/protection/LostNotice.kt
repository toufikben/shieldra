package com.shieldra.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * A "this phone is lost" notice for whoever finds the phone (A-33 next stage). The owner turns it on
 * remotely (`/lost`) and off (`/found`). It shows the owner's own contact on the lock screen so an
 * honest finder can return the phone — it reveals only what the owner chose to show, and nothing
 * about the guards. Unlike a full-screen takeover it needs no special permission.
 */
object LostNotice {
    private const val CHANNEL_ID = "shieldra_lost"
    private const val NOTIFICATION_ID = 20_020

    fun show(context: Context, contact: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(com.shieldra.app.R.string.lost_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val text = buildString {
            append(message.ifBlank { context.getString(com.shieldra.app.R.string.lost_default_message) })
            if (contact.isNotBlank()) append("\n").append(context.getString(com.shieldra.app.R.string.lost_contact, contact))
        }
        val notice = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(com.shieldra.app.R.string.lost_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        manager.notify(NOTIFICATION_ID, notice)
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
