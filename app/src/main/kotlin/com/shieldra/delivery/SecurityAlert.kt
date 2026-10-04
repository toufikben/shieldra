package com.shieldra.delivery

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What an alert is about. [TEST] is the owner checking a channel; it belongs to no recorded event. */
enum class AlertKind { PANIC, MOTION, TEST }

/**
 * One alert leaving the phone (amendment A-32). It carries only facts the app really has: which
 * guard, when, which phone, and the battery level. Photo and location join in a later stage, once
 * they are captured; nothing here invents them.
 */
data class SecurityAlert(
    /** The recorded event this alert is about, or null for a channel test. */
    val eventId: String?,
    val kind: AlertKind,
    val occurredAt: Instant,
    val device: String,
    val batteryPercent: Int?,
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
)

/** Builds the subject and body every channel sends, so Telegram and email say the same thing. */
object AlertText {
    fun subject(alert: SecurityAlert, wording: AlertWording): String =
        "${wording.appName} — ${title(alert.kind, wording)}"

    fun body(alert: SecurityAlert, wording: AlertWording, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", wording.locale)
            .withZone(zone)
            .format(alert.occurredAt)
        return buildString {
            append("🚨 ").append(subject(alert, wording)).append('\n')
            append("🕒 ").append(wording.timeLabel).append(": ").append(time).append(" (").append(zone.id).append(")\n")
            append("📱 ").append(wording.deviceLabel).append(": ").append(alert.device).append('\n')
            alert.batteryPercent?.let { append("🔋 ").append(wording.batteryLabel).append(": ").append(it).append("%\n") }
            alert.eventId?.let { append("🆔 ").append(wording.eventLabel).append(": ").append(it).append('\n') }
        }.trimEnd()
    }

    private fun title(kind: AlertKind, wording: AlertWording): String = when (kind) {
        AlertKind.PANIC -> wording.panicTitle
        AlertKind.MOTION -> wording.motionTitle
        AlertKind.TEST -> wording.testTitle
    }
}
