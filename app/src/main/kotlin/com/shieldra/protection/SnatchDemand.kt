package com.shieldra.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import java.util.Locale

/**
 * The notice a snatch leaves on the lock screen (A-46, restoring the A-45 demand as a resource-built
 * notice rather than a hand-typed string).
 *
 * It is public on purpose: the person holding a stolen phone must be able to read, in Arabic, English
 * and French, that the phone was taken and how to reach its owner. It shows the ten-minute countdown
 * while ownership is unproven, and then the cadence of the evidence leaving the phone. The notice is
 * ongoing — closing the app or swiping it away must not be how a thief silences the story — and it is
 * rebuilt at every ladder tick, so the text follows the phase instead of freezing at the first minute.
 *
 * The channel is new (`shieldra_snatch_v1`): Android freezes a channel's sound and visibility at
 * creation, so an upgrade must not inherit an existing channel's quiet settings. The demand is the only
 * notification that is allowed on the lock screen with its real content; every other notice stays
 * SECRET (see [ProtectionService]).
 */
object SnatchDemand {

    const val CHANNEL_ID = "shieldra_snatch_v1"
    const val NOTIFICATION_ID = 20_022

    private const val REQUEST_OPEN = 20_026
    private const val REQUEST_DIAL = 20_027

    fun show(context: Context, incident: SnatchPolicy.Incident, settings: ProtectionSettings) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(app, manager)
        val english = localized(app, "en")
        val body = SnatchText.compose(
            arabic = localized(app, "ar").getString(com.shieldra.app.R.string.snatch_demand_body),
            english = english.getString(com.shieldra.app.R.string.snatch_demand_body),
            french = localized(app, "fr").getString(com.shieldra.app.R.string.snatch_demand_body),
            ownerMessage = settings.lostMessage.trim().takeIf { it.isNotEmpty() }
                ?.let { english.getString(com.shieldra.app.R.string.snatch_demand_owner_message) + " " + it },
            contactLine = settings.ownerContact.trim().takeIf { it.isNotEmpty() }
                ?.let { english.getString(com.shieldra.app.R.string.snatch_demand_contact, it) },
        )
        val status = when (incident.phase) {
            SnatchPolicy.Phase.ESCALATING -> app.getString(
                com.shieldra.app.R.string.snatch_demand_escalating,
                SnatchPolicy.intervalMs(
                    settings.snatchIntervalMinutes,
                    BatteryLevel.percent(app),
                    BatteryLevel.isCharging(app),
                ) / 60_000L,
            )

            else -> app.getString(com.shieldra.app.R.string.snatch_demand_waiting)
        }
        val builder = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(app.getString(com.shieldra.app.R.string.snatch_demand_title))
            .setContentText(status)
            .setStyle(Notification.BigTextStyle().bigText("$body\n\n$status"))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(
                if (incident.phase == SnatchPolicy.Phase.AWAITING_PROOF) {
                    incident.startedAtMs + SnatchPolicy.PROOF_WINDOW_MS
                } else {
                    System.currentTimeMillis()
                },
            )
            .setUsesChronometer(incident.phase == SnatchPolicy.Phase.AWAITING_PROOF)
        launchIntent(app)?.let { builder.setContentIntent(it) }
        settings.ownerContact.trim().takeIf { it.isNotEmpty() }
            ?.let(OwnerContact::dialUri)
            ?.let { uri ->
                val dial = PendingIntent.getActivity(
                    app,
                    REQUEST_DIAL,
                    Intent(Intent.ACTION_DIAL, Uri.parse(uri)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, english.getString(com.shieldra.app.R.string.snatch_demand_call), dial)
            }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    /** Removes the demand. The incident is over — by proof, by an owner disarm, or by the six-hour cap. */
    fun hide(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(app: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ID,
            app.getString(com.shieldra.app.R.string.snatch_demand_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = app.getString(com.shieldra.app.R.string.snatch_demand_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 600L, 400L, 600L, 1_200L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && alarm != null) {
                setSound(
                    alarm,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
        manager.createNotificationChannel(channel)
    }

    private fun launchIntent(app: Context): PendingIntent? =
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
            PendingIntent.getActivity(
                app,
                REQUEST_OPEN,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    /** The same line in one named language, whatever language the phone is set to. */
    private fun localized(context: Context, tag: String): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }
}
