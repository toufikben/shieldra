package com.shieldra.delivery

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import android.os.Build
import com.shieldra.app.R
import com.shieldra.app.ShieldraApp
import com.shieldra.storage.SecretText
import java.time.Instant

/**
 * The ordered queue behind [PreferencesPendingAlertStore], kept free of Android types so its ordering
 * and retirement rules are provable off-device. Each entry carries the moment it happened, because the
 * encoded text alone sorts a recent incident behind an old one.
 */
class PendingAlertQueue(private val capacity: Int) {
    private val entries = mutableListOf<Entry>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    /** Adds one entry, keeping the newest [capacity] by occurrence time. */
    @Synchronized
    fun add(key: String, occurredAtMs: Long) {
        entries += Entry(key, occurredAtMs)
        if (entries.size > capacity) {
            val kept = entries.sortedBy { it.occurredAtMs }.takeLast(capacity)
            entries.clear()
            entries += kept
        }
    }

    /** Everything waiting, oldest first. Reading never removes anything. */
    @Synchronized
    fun pending(): List<Entry> = entries.sortedBy { it.occurredAtMs }

    /** Retires exactly one entry: what really went out, never the whole batch. */
    @Synchronized
    fun markSent(key: String) {
        entries.removeAll { it.key == key }
    }

    @Synchronized
    fun replaceAll(replacement: List<Entry>) {
        entries.clear()
        entries += replacement.sortedBy { it.occurredAtMs }.takeLast(capacity)
    }

    data class Entry(val key: String, val occurredAtMs: Long)
}

/**
 * [PendingAlertStore] in app-private preferences: a few short lines, and the one that holds a
 * position is sealed before it is written (audit §5.13). An alert waits here for as long as the phone
 * stays offline — which, during a theft, is exactly when its coordinates must not be readable.
 */
class PreferencesPendingAlertStore(
    context: Context,
    private val locationSecret: SecretText = SecretText(context.applicationContext, FIELD_LOCATION),
) : PendingAlertStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun add(alert: SecurityAlert) {
        val queue = PendingAlertQueue(MAX_PENDING)
        queue.replaceAll(readEntries())
        queue.add(AlertLineCodec.encode(alert, ::sealedLocation), alert.occurredAt.toEpochMilli())
        writeEntries(queue.pending())
    }

    @Synchronized
    override fun pending(): List<QueuedAlert> {
        val queue = PendingAlertQueue(MAX_PENDING)
        queue.replaceAll(readEntries())
        return queue.pending().mapNotNull { entry ->
            AlertLineCodec.decode(entry.key, locationSecret::open)?.let { QueuedAlert(entry.key, it) }
        }
    }

    @Synchronized
    override fun markSent(key: String) {
        val queue = PendingAlertQueue(MAX_PENDING)
        queue.replaceAll(readEntries())
        queue.markSent(key)
        writeEntries(queue.pending())
    }

    /**
     * Sealed, or an empty field: a position this build cannot seal is dropped rather than written in
     * the clear. The alert still carries the time, the guard and the battery, so the owner still gets
     * the news — only the map pin is sacrificed, and the policy that promises encryption is kept.
     */
    private fun sealedLocation(location: AlertLocation): String =
        locationSecret.seal(AlertLocationLine.format(location)) ?: ""

    /**
     * Each stored line carries its own occurrence stamp, so ordering never depends on the encoded
     * text. A line written by an older build has no stamp: its own time field supplies one, and a line
     * with neither is read as oldest rather than silently jumping the queue.
     */
    private fun readEntries(): List<PendingAlertQueue.Entry> =
        prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().map { stored ->
            val separator = stored.indexOf(STAMP_SEPARATOR)
            if (separator < 0) {
                PendingAlertQueue.Entry(stored, AlertLineCodec.occurredAtMs(stored) ?: 0L)
            } else {
                PendingAlertQueue.Entry(
                    key = stored.substring(separator + STAMP_SEPARATOR.length),
                    occurredAtMs = stored.substring(0, separator).toLongOrNull() ?: 0L,
                )
            }
        }

    private fun writeEntries(entries: List<PendingAlertQueue.Entry>) {
        prefs.edit()
            .putStringSet(KEY_PENDING, entries.map { "${it.occurredAtMs}$STAMP_SEPARATOR${it.key}" }.toSet())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "shieldra_pending_alerts"
        const val KEY_PENDING = "pending"
        /** Separates the occurrence stamp from the encoded line; never appears in the line itself. */
        const val STAMP_SEPARATOR = "\u0001"
        /** Bound as additional authenticated data, so a sealed position cannot be replayed elsewhere. */
        const val FIELD_LOCATION = "queued_alert_location"
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
            simTitle = resources.getString(R.string.alert_title_sim),
            batteryTitle = resources.getString(R.string.alert_title_battery),
            countdownTitle = resources.getString(R.string.alert_title_countdown),
            trackingTitle = resources.getString(R.string.alert_title_tracking),
            shutdownTitle = resources.getString(R.string.alert_title_shutdown),
            restartTitle = resources.getString(R.string.alert_title_restart),
            guardSilentTitle = resources.getString(R.string.alert_title_guard_silent),
            tamperTitle = resources.getString(R.string.alert_title_tamper),
            silentForLabel = resources.getString(R.string.alert_label_silent_for),
            locationLabel = resources.getString(R.string.alert_label_location),
            locationUnavailable = resources.getString(R.string.alert_location_unavailable),
            accuracyLabel = resources.getString(R.string.alert_label_accuracy),
            fixAgeLabel = resources.getString(R.string.alert_label_fix_age),
            photoLabel = resources.getString(R.string.alert_label_photo),
            photoAttached = resources.getString(R.string.alert_photo_attached),
            photoUnavailable = resources.getString(R.string.alert_photo_unavailable),
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
