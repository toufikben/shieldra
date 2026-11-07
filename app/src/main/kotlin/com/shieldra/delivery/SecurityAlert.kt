package com.shieldra.delivery

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What an alert is about. [TEST] is the owner checking a channel; it belongs to no recorded event.
 * [LOCK] is a wrong PIN/pattern/password past the owner's threshold, [COUNTDOWN] the owner's own
 * periodic signal (A-33): neither is announced on the phone. [RESTART] is the phone coming back from a
 * reboot that the guards could not watch through (audit E-2): it belongs to no event either, because
 * nothing was seen while the phone was dark — the point is that the owner hears about the gap.
 * [GUARD_SILENT] is the watchdog finding no guard stamp for longer than the tolerance window (audit
 * development أ): also no event, because the whole news is the absence of activity.
 */
enum class AlertKind { PANIC, MOTION, LOCK, SIM, BATTERY, COUNTDOWN, TRACKING, SHUTDOWN, RESTART, GUARD_SILENT, TEST }

/** A position fix the phone really produced: never a guess and never a default. */
data class AlertLocation(
    val latitude: Double,
    val longitude: Double,
    /** Radius of 68 % confidence in metres, when the provider reported one. */
    val accuracyMeters: Float?,
    /** When the fix itself was taken; an old cached fix is shown with its own age. */
    val fixedAt: Instant,
    val provider: String,
)

/**
 * One alert leaving the phone (amendments A-32, A-33). It carries only facts the app really has:
 * which guard, when, which phone, the battery level and — when the protection service could take
 * them — a front-camera photo and a location fix. A missing photo or fix is said, never invented.
 */
data class SecurityAlert(
    /** The recorded event this alert is about, or null for a channel test. */
    val eventId: String?,
    val kind: AlertKind,
    val occurredAt: Instant,
    val device: String,
    val batteryPercent: Int?,
    val location: AlertLocation? = null,
    /** Id of the encrypted front-camera photo; the dispatcher opens it just before sending. */
    val photoEvidenceId: String? = null,
    /** Id of the encrypted back-camera photo, when dual-camera capture is on. */
    val photoBackEvidenceId: String? = null,
    /** Id of the encrypted short audio clip, when audio capture is on. */
    val audioEvidenceId: String? = null,
    /** True when a photo and a fix were attempted, so their absence is reported rather than hidden. */
    val evidenceAttempted: Boolean = false,
    /** Wrong unlock attempts counted so far, for a [AlertKind.LOCK] alert. */
    val failedAttempts: Int? = null,
    /**
     * How long the guards have been silent, for a [AlertKind.GUARD_SILENT] alert. Measured from the
     * last stamp the service wrote, so it is the exposure window the owner actually had — not a guess
     * from when the alarm happened to fire.
     */
    val silentMinutes: Long? = null,
)

/** The localized wording an alert is written with; resolved by the caller from resources. */
data class AlertWording(
    val appName: String,
    val panicTitle: String,
    val motionTitle: String,
    val testTitle: String,
    val timeLabel: String,
    val deviceLabel: String,
    val batteryLabel: String,
    val eventLabel: String,
    val locale: Locale,
    val lockTitle: String = "Wrong unlock attempts",
    val simTitle: String = "SIM changed",
    val batteryTitle: String = "Low battery",
    val countdownTitle: String = "Periodic signal",
    val trackingTitle: String = "Live location",
    val shutdownTitle: String = "Phone is shutting down",
    val restartTitle: String = "Phone was restarted",
    val guardSilentTitle: String = "Guards have gone quiet",
    /** Carries the measured silence, so the owner learns how long the phone was unprotected. */
    val silentForLabel: String = "Guards silent for %1\$d min",
    val locationLabel: String = "Location",
    val locationUnavailable: String = "not available",
    val accuracyLabel: String = "accuracy",
    val fixAgeLabel: String = "fix taken",
    val photoLabel: String = "Front camera",
    val photoAttached: String = "photo attached",
    val photoUnavailable: String = "no photo could be taken",
    val attemptsLabel: String = "Failed attempts",
)

/** Builds the subject and body every channel sends, so Telegram and email say the same thing. */
object AlertText {
    fun subject(alert: SecurityAlert, wording: AlertWording): String =
        "${wording.appName} — ${title(alert.kind, wording)}"

    fun body(
        alert: SecurityAlert,
        wording: AlertWording,
        zone: ZoneId = ZoneId.systemDefault(),
        photoAttached: Boolean = false,
    ): String {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", wording.locale).withZone(zone)
        return buildString {
            append("🚨 ").append(subject(alert, wording)).append('\n')
            append("🕒 ").append(wording.timeLabel).append(": ").append(format.format(alert.occurredAt))
                .append(" (").append(zone.id).append(")\n")
            alert.failedAttempts?.let { append("🔢 ").append(wording.attemptsLabel).append(": ").append(it).append('\n') }
            alert.silentMinutes?.let {
                append("⏳ ")
                    .append(String.format(wording.locale, wording.silentForLabel, it))
                    .append('\n')
            }
            val location = alert.location
            if (location != null) {
                append("📍 ").append(wording.locationLabel).append(": ")
                    .append(coordinate(location.latitude)).append(", ").append(coordinate(location.longitude))
                location.accuracyMeters?.let { append(" (").append(wording.accuracyLabel).append(" ±").append(it.toInt()).append(" m)") }
                append('\n')
                append("🗺️ ").append(mapsLink(location)).append('\n')
                append("⏱️ ").append(wording.fixAgeLabel).append(": ").append(format.format(location.fixedAt))
                    .append(" [").append(location.provider).append("]\n")
            } else if (alert.evidenceAttempted) {
                append("📍 ").append(wording.locationLabel).append(": ").append(wording.locationUnavailable).append('\n')
            }
            if (alert.evidenceAttempted || photoAttached) {
                append("📷 ").append(wording.photoLabel).append(": ")
                    .append(if (photoAttached) wording.photoAttached else wording.photoUnavailable).append('\n')
            }
            append("📱 ").append(wording.deviceLabel).append(": ").append(alert.device).append('\n')
            alert.batteryPercent?.let { append("🔋 ").append(wording.batteryLabel).append(": ").append(it).append("%\n") }
            alert.eventId?.let { append("🆔 ").append(wording.eventLabel).append(": ").append(it).append('\n') }
        }.trimEnd()
    }

    /** A link any phone opens in its map app; plain coordinates, no tracking parameters. */
    fun mapsLink(location: AlertLocation): String =
        "https://maps.google.com/?q=${coordinate(location.latitude)},${coordinate(location.longitude)}"

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private fun title(kind: AlertKind, wording: AlertWording): String = when (kind) {
        AlertKind.PANIC -> wording.panicTitle
        AlertKind.MOTION -> wording.motionTitle
        AlertKind.LOCK -> wording.lockTitle
        AlertKind.SIM -> wording.simTitle
        AlertKind.BATTERY -> wording.batteryTitle
        AlertKind.COUNTDOWN -> wording.countdownTitle
        AlertKind.TRACKING -> wording.trackingTitle
        AlertKind.SHUTDOWN -> wording.shutdownTitle
        AlertKind.RESTART -> wording.restartTitle
        AlertKind.GUARD_SILENT -> wording.guardSilentTitle
        AlertKind.TEST -> wording.testTitle
    }
}

/**
 * What travels with the text (all already decrypted): the front photo, an optional back photo, an
 * optional short audio clip, and the fix for a map pin. The audio carries its own media type so a
 * channel sends it correctly.
 */
class AlertExtras(
    val photoJpeg: ByteArray? = null,
    val photoBackJpeg: ByteArray? = null,
    val audioBytes: ByteArray? = null,
    val audioMediaType: String = "audio/mp4",
    val location: AlertLocation? = null,
) {
    companion object {
        val NONE = AlertExtras()
    }
}
