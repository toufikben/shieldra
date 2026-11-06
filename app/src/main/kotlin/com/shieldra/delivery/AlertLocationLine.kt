package com.shieldra.delivery

import java.time.Instant

/**
 * The one line a queued alert carries its position in (audit B-2/§5.13).
 *
 * An alert sits in the pending store for as long as the phone stays out of contact — which during a
 * theft is exactly when its coordinates must not be readable off the disk. So the line is sealed by
 * `com.shieldra.storage.SecretText` before it is stored, and this object holds only the shape of the
 * position inside the seal: latitude;longitude;accuracyMeters;fix time;provider.
 *
 * [parse] also reads the plain spelling that builds before this one wrote, because alerts queued by an
 * older install must still be deliverable after an update; nothing new is ever written that way.
 */
object AlertLocationLine {
    /** The character the alert queue splits its fields on; a position must never contain it. */
    private const val FIELD_BREAK = "|"

    fun format(location: AlertLocation): String = listOf(
        location.latitude.toString(),
        location.longitude.toString(),
        location.accuracyMeters?.toString().orEmpty(),
        location.fixedAt.toEpochMilli().toString(),
        location.provider.replace(FIELD_BREAK, " "),
    ).joinToString(";")

    /** A line that is not a position — damaged, or written by a build that stored something else. */
    fun parse(raw: String): AlertLocation? = runCatching {
        val fields = raw.split(";")
        AlertLocation(
            latitude = fields[0].toDouble(),
            longitude = fields[1].toDouble(),
            accuracyMeters = fields[2].toFloatOrNull(),
            fixedAt = Instant.ofEpochMilli(fields[3].toLong()),
            provider = fields[4],
        )
    }.getOrNull()
}
