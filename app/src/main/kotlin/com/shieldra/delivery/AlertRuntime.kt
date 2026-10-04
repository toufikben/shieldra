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
        alert.device.replace(SEPARATOR, " "),
    ).joinToString(SEPARATOR)

    private fun decode(line: String): SecurityAlert? = runCatching {
        val parts = line.split(SEPARATOR, limit = 5)
        SecurityAlert(
            eventId = parts[2].ifBlank { null },
            kind = AlertKind.valueOf(parts[1]),
            occurredAt = Instant.ofEpochMilli(parts[0].toLong()),
            device = parts[4],
            batteryPercent = parts[3].toIntOrNull(),
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
        )
    }

    private fun build(app: Context): AlertDispatcher {
        val settingsStore = DeliverySettingsStore(app)
        return AlertDispatcher(
            settings = { settingsStore.load() },
            repository = { (app as? ShieldraApp)?.installedStorage?.eventRepository },
            wording = { wording(app) },
            pending = PreferencesPendingAlertStore(app),
        )
    }

    private fun batteryPercent(context: Context): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }.getOrNull()
}
