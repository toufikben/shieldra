package com.shieldra.delivery

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.Build
import com.shieldra.app.R
import com.shieldra.app.ShieldraApp
import java.time.Instant

/** [PendingAlertStore] in app-private preferences: a few short lines, never a secret. */
class PreferencesPendingAlertStore(context: Context) : PendingAlertStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun add(alert: SecurityAlert) {
        val lines = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty() + encode(alert)
        prefs.edit().putStringSet(KEY_PENDING, lines.sortedBy { it }.takeLast(MAX_PENDING).toSet()).apply()
    }

    @Synchronized
    override fun drain(): List<SecurityAlert> {
        val lines = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty()
        prefs.edit().remove(KEY_PENDING).apply()
        return lines.mapNotNull(::decode).sortedBy { it.occurredAt }
    }

    private fun encode(alert: SecurityAlert): String = listOf(
        alert.occurredAt.toEpochMilli().toString(),
        alert.kind.name,
        alert.eventId.orEmpty(),
        alert.batteryPercent?.toString().orEmpty(),
        alert.photoEvidenceId.orEmpty(),
        if (alert.evidenceAttempted) "1" else "0",
        alert.failedAttempts?.toString().orEmpty(),
        alert.location?.let { "${it.latitude};${it.longitude};${it.accuracyMeters ?: ""};${it.fixedAt.toEpochMilli()};${it.provider}" }.orEmpty(),
        alert.photoBackEvidenceId.orEmpty(),
        alert.audioEvidenceId.orEmpty(),
        alert.device.replace(SEPARATOR, " "),
    ).joinToString(SEPARATOR)

    private fun decode(line: String): SecurityAlert? = runCatching {
        val parts = line.split(SEPARATOR, limit = 11)
        SecurityAlert(
            eventId = parts[2].ifBlank { null },
            kind = AlertKind.valueOf(parts[1]),
            occurredAt = Instant.ofEpochMilli(parts[0].toLong()),
            device = parts[10],
            batteryPercent = parts[3].toIntOrNull(),
            photoEvidenceId = parts[4].ifBlank { null },
            evidenceAttempted = parts[5] == "1",
            failedAttempts = parts[6].toIntOrNull(),
            location = parts[7].ifBlank { null }?.let { decodeLocation(it) },
            photoBackEvidenceId = parts[8].ifBlank { null },
            audioEvidenceId = parts[9].ifBlank { null },
        )
    }.getOrNull()

    private fun decodeLocation(raw: String): AlertLocation? = runCatching {
        val f = raw.split(";")
        AlertLocation(
            latitude = f[0].toDouble(),
            longitude = f[1].toDouble(),
            accuracyMeters = f[2].toFloatOrNull(),
            fixedAt = Instant.ofEpochMilli(f[3].toLong()),
            provider = f[4],
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "shieldra_pending_alerts"
        const val KEY_PENDING = "pending"
        const val SEPARATOR = "|"
        /** Oldest are dropped past this: the newest alerts are the ones that matter after a theft. */
        const val MAX_PENDING = 50
    }
}

/**
 * The one process-wide alert sender (A-32). Built lazily from the installed storage, the owner's
 * saved channels and the current language, so Panic, Motion and Settings all share one queue.
 */
object AlertRuntime {
    @Volatile
    private var dispatcher: AlertDispatcher? = null

    fun dispatcher(context: Context): AlertDispatcher {
        dispatcher?.let { return it }
        return synchronized(this) {
            dispatcher ?: build(context.applicationContext).also { dispatcher = it }
        }
    }

    /** Sends [kind] for a recorded event, silently and off the caller's thread. */
    fun alert(context: Context, kind: AlertKind, eventId: String?) {
        dispatcher(context).dispatch(snapshot(context, kind, eventId))
    }

    /** Sends a fully-built alert (with photo evidence and a fix); used by the protection service. */
    fun alert(context: Context, alert: SecurityAlert) {
        dispatcher(context).dispatch(alert)
    }

    /** The facts an alert carries right now: time, phone model, battery. */
    fun snapshot(context: Context, kind: AlertKind, eventId: String?): SecurityAlert = SecurityAlert(
        eventId = eventId,
        kind = kind,
        occurredAt = Instant.now(),
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        batteryPercent = batteryPercent(context),
    )

    /** Resends what was kept while offline the moment any network is usable again. */
    fun watchNetwork(context: Context) {
        val app = context.applicationContext
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivity.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        dispatcher(app).flushPending()
                    }
                },
            )
        }
    }

    fun wording(context: Context): AlertWording {
        val resources = context.resources
        return AlertWording(
            appName = resources.getString(R.string.app_name),
            panicTitle = resources.getString(R.string.alert_title_panic),
            motionTitle = resources.getString(R.string.alert_title_motion),
            testTitle = resources.getString(R.string.alert_title_test),
            timeLabel = resources.getString(R.string.alert_label_time),
            deviceLabel = resources.getString(R.string.alert_label_device),
            batteryLabel = resources.getString(R.string.alert_label_battery),
            eventLabel = resources.getString(R.string.alert_label_event),
            locale = resources.configuration.locales[0],
            lockTitle = resources.getString(R.string.alert_title_lock),
            simTitle = resources.getString(R.string.alert_title_sim),
            batteryTitle = resources.getString(R.string.alert_title_battery),
            countdownTitle = resources.getString(R.string.alert_title_countdown),
            trackingTitle = resources.getString(R.string.alert_title_tracking),
            locationLabel = resources.getString(R.string.alert_label_location),
            locationUnavailable = resources.getString(R.string.alert_location_unavailable),
            accuracyLabel = resources.getString(R.string.alert_label_accuracy),
            fixAgeLabel = resources.getString(R.string.alert_label_fix_age),
            photoLabel = resources.getString(R.string.alert_label_photo),
            photoAttached = resources.getString(R.string.alert_photo_attached),
            photoUnavailable = resources.getString(R.string.alert_photo_unavailable),
            attemptsLabel = resources.getString(R.string.alert_label_attempts),
        )
    }

    private fun build(app: Context): AlertDispatcher {
        val settingsStore = DeliverySettingsStore(app)
        return AlertDispatcher(
            settings = {
                val delivery = settingsStore.load()
                // The free plan sends by email only; Telegram is a paid channel (owner's split).
                val tier = com.shieldra.protection.ProtectionSettingsStore(app).load().tier
                if (com.shieldra.billing.TierGate.allowsTelegram(tier)) delivery else delivery.copy(telegramEnabled = false)
            },
            repository = { (app as? ShieldraApp)?.installedStorage?.eventRepository },
            wording = { wording(app) },
            pending = PreferencesPendingAlertStore(app),
            extras = { alert -> openExtras(app, alert) },
        )
    }

    /**
     * Opens the encrypted front-camera photo for [alert] and carries its fix, just before the send.
     * The decrypted bytes live only for this call; a failure to open them never blocks the text.
     */
    private fun openExtras(app: Context, alert: SecurityAlert): AlertExtras {
        fun open(id: String?): ByteArray? =
            if (id.isNullOrBlank()) null
            else runCatching { (app as? ShieldraApp)?.installedStorage?.evidenceFiles?.read(id) }.getOrNull()

        val photo = open(alert.photoEvidenceId)
        val back = open(alert.photoBackEvidenceId)
        val audio = open(alert.audioEvidenceId)
        return if (photo == null && back == null && audio == null && alert.location == null) AlertExtras.NONE
        else AlertExtras(photoJpeg = photo, photoBackJpeg = back, audioBytes = audio, location = alert.location)
    }

    private fun batteryPercent(context: Context): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }.getOrNull()
}
