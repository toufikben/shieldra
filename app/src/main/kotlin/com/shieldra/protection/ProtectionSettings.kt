package com.shieldra.protection

import android.content.Context
import android.content.SharedPreferences

/**
 * Everything the owner configured for the protection guards (A-33), in app-private preferences the
 * backup rules already exclude. No secret lives here — the delivery secrets stay in their own
 * Keystore-sealed store. [armed] is the one fact boot recovery reads: it is true only while the
 * owner has protection switched on, so a reboot resumes exactly what the owner last chose.
 */
data class ProtectionSettings(
    val armed: Boolean = false,
    val lockEnabled: Boolean = true,
    val simEnabled: Boolean = true,
    val batteryEnabled: Boolean = true,
    val lockThreshold: Int = FailedUnlockCounter.DEFAULT_THRESHOLD,
    val batteryThreshold: Int = BatteryBreathLogic.DEFAULT_LEVEL,
    val countdownEnabled: Boolean = false,
    val countdownIntervalMs: Long = 60 * 60_000L,
    val motionResponse: MotionResponse = MotionResponse.SILENT,
    val motionGraceSeconds: Int = 5,
    val retentionDays: Int = 30,
) {
    /** What table-mode does when the phone is lifted: a silent capture, a loud siren, or a grace first. */
    enum class MotionResponse { SILENT, SIREN, GRACE_THEN_SIREN }
}

class ProtectionSettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ProtectionSettings = ProtectionSettings(
        armed = prefs.getBoolean(KEY_ARMED, false),
        lockEnabled = prefs.getBoolean(KEY_LOCK, true),
        simEnabled = prefs.getBoolean(KEY_SIM, true),
        batteryEnabled = prefs.getBoolean(KEY_BATTERY, true),
        lockThreshold = prefs.getInt(KEY_LOCK_THRESHOLD, FailedUnlockCounter.DEFAULT_THRESHOLD),
        batteryThreshold = prefs.getInt(KEY_BATTERY_THRESHOLD, BatteryBreathLogic.DEFAULT_LEVEL),
        countdownEnabled = prefs.getBoolean(KEY_COUNTDOWN, false),
        countdownIntervalMs = prefs.getLong(KEY_COUNTDOWN_INTERVAL, 60 * 60_000L),
        motionResponse = runCatching {
            ProtectionSettings.MotionResponse.valueOf(
                prefs.getString(KEY_MOTION_RESPONSE, ProtectionSettings.MotionResponse.SILENT.name).orEmpty(),
            )
        }.getOrDefault(ProtectionSettings.MotionResponse.SILENT),
        motionGraceSeconds = prefs.getInt(KEY_MOTION_GRACE, 5),
        retentionDays = prefs.getInt(KEY_RETENTION, 30),
    )

    fun save(settings: ProtectionSettings) {
        prefs.edit()
            .putBoolean(KEY_ARMED, settings.armed)
            .putBoolean(KEY_LOCK, settings.lockEnabled)
            .putBoolean(KEY_SIM, settings.simEnabled)
            .putBoolean(KEY_BATTERY, settings.batteryEnabled)
            .putInt(KEY_LOCK_THRESHOLD, settings.lockThreshold.coerceIn(FailedUnlockCounter.MIN_THRESHOLD, FailedUnlockCounter.MAX_THRESHOLD))
            .putInt(KEY_BATTERY_THRESHOLD, settings.batteryThreshold.coerceIn(BatteryBreathLogic.MIN_LEVEL, BatteryBreathLogic.MAX_LEVEL))
            .putBoolean(KEY_COUNTDOWN, settings.countdownEnabled)
            .putLong(KEY_COUNTDOWN_INTERVAL, settings.countdownIntervalMs)
            .putString(KEY_MOTION_RESPONSE, settings.motionResponse.name)
            .putInt(KEY_MOTION_GRACE, settings.motionGraceSeconds)
            .putInt(KEY_RETENTION, settings.retentionDays)
            .apply()
    }

    fun setArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_ARMED, armed).apply()
    }

    /** The SIM identities present when protection was armed; the SIM guard compares against these. */
    fun simBaseline(): Set<String> = prefs.getStringSet(KEY_SIM_BASELINE, emptySet()).orEmpty()

    fun setSimBaseline(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SIM_BASELINE, ids).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "shieldra_protection"
        const val KEY_ARMED = "armed"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_SIM = "sim_enabled"
        const val KEY_BATTERY = "battery_enabled"
        const val KEY_LOCK_THRESHOLD = "lock_threshold"
        const val KEY_BATTERY_THRESHOLD = "battery_threshold"
        const val KEY_COUNTDOWN = "countdown_enabled"
        const val KEY_COUNTDOWN_INTERVAL = "countdown_interval_ms"
        const val KEY_MOTION_RESPONSE = "motion_response"
        const val KEY_MOTION_GRACE = "motion_grace_seconds"
        const val KEY_RETENTION = "retention_days"
        const val KEY_SIM_BASELINE = "sim_baseline"
    }
}
