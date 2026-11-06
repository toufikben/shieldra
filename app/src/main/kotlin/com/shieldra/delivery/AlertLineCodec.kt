package com.shieldra.delivery

import com.shieldra.storage.SecretText
import java.time.Instant

/**
 * The one line a queued alert is stored as, and back again (A-32). Pulled out of the preferences store
 * so the round trip can be argued on the JVM instead of on a phone: the queue is the part of delivery
 * that only ever runs while the owner is offline, which is exactly the part a static reviewer cannot
 * see working.
 *
 * Fields are positional and a new one is only ever **appended**, because a queue written by an older
 * build has to stay readable after an update — the alerts sitting in it were taken during a theft, and
 * dropping them to keep a format tidy would lose the evidence itself. Reading past the end of an old
 * line yields null, never a failure.
 *
 * The position is the exception: it is handed to [seal] on the way out and to [open] on the way back
 * (audit §5.13), so this object never sees a key and the stored line never carries a raw coordinate.
 */
object AlertLineCodec {
    const val SEPARATOR = "|"

    /** How many fields a line carries; the last is optional for the sake of lines already queued. */
    private const val FIELD_COUNT = 12

    fun encode(alert: SecurityAlert, seal: (AlertLocation) -> String?): String = listOf(
        alert.occurredAt.toEpochMilli().toString(),
        alert.kind.name,
        alert.eventId.orEmpty(),
        alert.batteryPercent?.toString().orEmpty(),
        alert.photoEvidenceId.orEmpty(),
        if (alert.evidenceAttempted) "1" else "0",
        // Field 6 used to hold the Lock guard's failure count. It is kept as an empty slot because
        // every field after it is positional: a shift would read a sealed location as a photo id.
        "",
        alert.location?.let(seal).orEmpty(),
        alert.photoBackEvidenceId.orEmpty(),
        alert.audioEvidenceId.orEmpty(),
        alert.device.replace(SEPARATOR, " "),
        alert.silentMinutes?.toString().orEmpty(),
    ).joinToString(SEPARATOR)

    /** Null for a line this build cannot make sense of; a line that is merely old still decodes. */
    fun decode(line: String, open: (String) -> String?): SecurityAlert? = runCatching {
        val parts = line.split(SEPARATOR, limit = FIELD_COUNT)
        SecurityAlert(
            eventId = parts[2].ifBlank { null },
            kind = AlertKind.valueOf(parts[1]),
            occurredAt = Instant.ofEpochMilli(parts[0].toLong()),
            device = parts[10],
            batteryPercent = parts[3].toIntOrNull(),
            photoEvidenceId = parts[4].ifBlank { null },
            evidenceAttempted = parts[5] == "1",
            location = readLocation(parts[7], open),
            photoBackEvidenceId = parts[8].ifBlank { null },
            audioEvidenceId = parts[9].ifBlank { null },
            silentMinutes = parts.getOrNull(11)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
        )
    }.getOrNull()

    /**
     * A sealed position is opened with the key; anything else is a position an older build had already
     * queued before this field was sealed, and is read as written. A position that cannot be opened is
     * lost, the alert around it is not: the owner still hears that something happened.
     */
    private fun readLocation(raw: String, open: (String) -> String?): AlertLocation? = when {
        raw.isBlank() -> null
        SecretText.isSealed(raw) -> open(raw)?.let(AlertLocationLine::parse)
        else -> AlertLocationLine.parse(raw)
    }
}
