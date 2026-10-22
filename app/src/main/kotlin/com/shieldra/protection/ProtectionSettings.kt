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
    val remoteCommandsEnabled: Boolean = false,
    val liveTrackingEnabled: Boolean = false,
    val trackingIntervalMinutes: Int = 2,
    val trackingDurationMinutes: Int = 30,
    val dualCameraEnabled: Boolean = false,
    val audioEnabled: Boolean = false,
    val audioSeconds: Int = 10,
    val escalationEnabled: Boolean = false,
    val escalationThreshold: Int = EscalationPolicy.DEFAULT_THRESHOLD,
    val geofenceEnabled: Boolean = false,
    val geofenceIntervalMinutes: Int = 3,
    val ownerContact: String = "",
    val lostMessage: String = "",
    /** The owner's plan; PREMIUM until real billing is wired, so nothing is locked during testing. */
    val tier: com.shieldra.billing.Tier = com.shieldra.billing.Tier.PREMIUM,
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
        remoteCommandsEnabled = prefs.getBoolean(KEY_REMOTE, false),
        liveTrackingEnabled = prefs.getBoolean(KEY_TRACK, false),
        trackingIntervalMinutes = prefs.getInt(KEY_TRACK_INTERVAL, 2),
        trackingDurationMinutes = prefs.getInt(KEY_TRACK_DURATION, 30),
        dualCameraEnabled = prefs.getBoolean(KEY_DUAL_CAM, false),
        audioEnabled = prefs.getBoolean(KEY_AUDIO, false),
        audioSeconds = prefs.getInt(KEY_AUDIO_SECONDS, 10),
        escalationEnabled = prefs.getBoolean(KEY_ESCALATION, false),
        escalationThreshold = prefs.getInt(KEY_ESCALATION_THRESHOLD, EscalationPolicy.DEFAULT_THRESHOLD),
        geofenceEnabled = prefs.getBoolean(KEY_GEOFENCE, false),
        geofenceIntervalMinutes = prefs.getInt(KEY_GEOFENCE_INTERVAL, 3),
        ownerContact = prefs.getString(KEY_OWNER_CONTACT, "").orEmpty(),
        lostMessage = prefs.getString(KEY_LOST_MESSAGE, "").orEmpty(),
        tier = runCatching {
            com.shieldra.billing.Tier.valueOf(prefs.getString(KEY_TIER, com.shieldra.billing.Tier.PREMIUM.name).orEmpty())
        }.getOrDefault(com.shieldra.billing.Tier.PREMIUM),
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
            .putBoolean(KEY_REMOTE, settings.remoteCommandsEnabled)
            .putBoolean(KEY_TRACK, settings.liveTrackingEnabled)
            .putInt(KEY_TRACK_INTERVAL, settings.trackingIntervalMinutes)
            .putInt(KEY_TRACK_DURATION, settings.trackingDurationMinutes)
            .putBoolean(KEY_DUAL_CAM, settings.dualCameraEnabled)
            .putBoolean(KEY_AUDIO, settings.audioEnabled)
            .putInt(KEY_AUDIO_SECONDS, settings.audioSeconds)
            .putBoolean(KEY_ESCALATION, settings.escalationEnabled)
            .putInt(KEY_ESCALATION_THRESHOLD, settings.escalationThreshold.coerceIn(EscalationPolicy.MIN_THRESHOLD, EscalationPolicy.MAX_THRESHOLD))
            .putBoolean(KEY_GEOFENCE, settings.geofenceEnabled)
            .putInt(KEY_GEOFENCE_INTERVAL, settings.geofenceIntervalMinutes.coerceIn(1, 60))
            .putString(KEY_OWNER_CONTACT, settings.ownerContact.trim())
            .putString(KEY_LOST_MESSAGE, settings.lostMessage.trim())
            .putString(KEY_TIER, settings.tier.name)
            .apply()
    }

    fun setArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_ARMED, armed).apply()
    }

    /** The owner's safe zones (A-36); leaving them all while armed alerts. */
    fun safeZones(): List<SafeZone> =
        prefs.getStringSet(KEY_SAFE_ZONES, emptySet()).orEmpty().mapNotNull(::decodeZone)
            .sortedBy { it.name }

    fun setSafeZones(zones: List<SafeZone>) {
        prefs.edit().putStringSet(KEY_SAFE_ZONES, zones.map(::encodeZone).toSet()).apply()
    }

    private fun encodeZone(z: SafeZone): String =
        listOf(z.name.replace('\u0001', ' '), z.zone.latitude, z.zone.longitude, z.zone.radiusMeters)
            .joinToString("\u0001")

    private fun decodeZone(raw: String): SafeZone? {
        val p = raw.split('\u0001')
        if (p.size != 4) return null
        val lat = p[1].toDoubleOrNull() ?: return null
        val lon = p[2].toDoubleOrNull() ?: return null
        val rad = p[3].toDoubleOrNull() ?: return null
        return SafeZone(p[0], GeofenceLogic.Zone(lat, lon, rad))
    }

    /** The SIM identities present when protection was armed; the SIM guard compares against these. */
    fun simBaseline(): Set<String> = prefs.getStringSet(KEY_SIM_BASELINE, emptySet()).orEmpty()

    fun setSimBaseline(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SIM_BASELINE, ids).apply()
    }

    /** The current countdown deadline, or null if no cycle is scheduled. */
    fun countdownDeadline(): CountdownDeadline? {
        if (!prefs.contains(KEY_CD_EPOCH)) return null
        return CountdownDeadline(
            epochDeadlineMs = prefs.getLong(KEY_CD_EPOCH, 0L),
            elapsedDeadlineMs = prefs.getLong(KEY_CD_ELAPSED, 0L),
        )
    }

    fun setCountdownDeadline(deadline: CountdownDeadline?) {
        val editor = prefs.edit()
        if (deadline == null) {
            editor.remove(KEY_CD_EPOCH).remove(KEY_CD_ELAPSED)
        } else {
            editor.putLong(KEY_CD_EPOCH, deadline.epochDeadlineMs).putLong(KEY_CD_ELAPSED, deadline.elapsedDeadlineMs)
        }
        editor.apply()
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
        const val KEY_REMOTE = "remote_commands_enabled"
        const val KEY_TRACK = "live_tracking_enabled"
        const val KEY_TRACK_INTERVAL = "tracking_interval_minutes"
        const val KEY_TRACK_DURATION = "tracking_duration_minutes"
        const val KEY_DUAL_CAM = "dual_camera_enabled"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_AUDIO_SECONDS = "audio_seconds"
        const val KEY_ESCALATION = "escalation_enabled"
        const val KEY_ESCALATION_THRESHOLD = "escalation_threshold"
        const val KEY_GEOFENCE = "geofence_enabled"
        const val KEY_GEOFENCE_INTERVAL = "geofence_interval_minutes"
        const val KEY_SAFE_ZONES = "safe_zones"
        const val KEY_OWNER_CONTACT = "owner_contact"
        const val KEY_LOST_MESSAGE = "lost_message"
        const val KEY_TIER = "tier"
        const val KEY_SIM_BASELINE = "sim_baseline"
        const val KEY_CD_EPOCH = "countdown_epoch_deadline"
        const val KEY_CD_ELAPSED = "countdown_elapsed_deadline"
    }
}
