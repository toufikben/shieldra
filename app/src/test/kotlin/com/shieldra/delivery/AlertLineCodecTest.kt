package com.shieldra.delivery

import com.shieldra.storage.SecretText
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The queued-alert line (A-32, audit §5.13 and development أ). This is the only part of delivery that
 * runs while the owner has no network, so it is argued here rather than on a phone — including what an
 * older build wrote, because the alerts sitting in that queue were taken during a theft.
 */
class AlertLineCodecTest {
    // The stand-in seal has the one property the real one is claimed for: the coordinates do not
    // survive in the clear. Base64 is not encryption — on a phone the AES-GCM seal in SecretText does
    // this — but it makes an accidental plaintext write fail here instead of passing quietly.
    private fun seal(location: AlertLocation): String =
        SecretText.SEALED_PREFIX +
            Base64.getEncoder().encodeToString(AlertLocationLine.format(location).toByteArray(Charsets.UTF_8))

    private fun open(raw: String): String? =
        raw.removePrefix(SecretText.SEALED_PREFIX).ifBlank { null }
            ?.let { runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrNull() }

    private fun alert(
        location: AlertLocation? = AlertLocation(36.75, 3.06, 12f, Instant.ofEpochMilli(1_700_000_000_000), "fused"),
        silentMinutes: Long? = null,
        failedAttempts: Int? = null,
    ) = SecurityAlert(
        eventId = "event-1",
        kind = AlertKind.MOTION,
        occurredAt = Instant.ofEpochMilli(1_700_000_001_000),
        device = "Realme RMX3910",
        batteryPercent = 42,
        location = location,
        photoEvidenceId = "photo-1",
        photoBackEvidenceId = "photo-back-1",
        audioEvidenceId = "audio-1",
        evidenceAttempted = true,
        failedAttempts = failedAttempts,
        silentMinutes = silentMinutes,
    )

    @Test
    fun a_queued_alert_comes_back_with_every_field_it_had() {
        val stored = AlertLineCodec.encode(alert(), ::seal)
        val restored = AlertLineCodec.decode(stored, ::open)
        assertEquals(alert(), restored)
    }

    @Test
    fun the_position_is_never_stored_as_coordinates_in_the_clear() {
        val stored = AlertLineCodec.encode(alert(), ::seal)
        assertFalse(stored.contains("36.75"))
        assertNotNull(AlertLineCodec.decode(stored, ::open)?.location)
    }

    @Test
    fun a_silence_duration_survives_the_queue() {
        val stored = AlertLineCodec.encode(alert(silentMinutes = 95L), ::seal)
        assertEquals(95L, AlertLineCodec.decode(stored, ::open)?.silentMinutes)
    }

    @Test
    /** A field added later must not make an already-queued alert unreadable: append, never insert. */
    fun a_line_written_by_an_older_build_still_decodes() {
        // Field 6 is the Lock guard's failure count an older build queued; the slot stayed in place
        // while the guard was removed, so the fields after it kept their positions — and this build
        // reads the count again instead of throwing it away.
        val legacy = listOf(
            "1700000001000",
            "MOTION",
            "event-1",
            "42",
            "photo-1",
            "1",
            "3",
            "36.75;3.06;12;1700000000000;fused",
            "photo-back-1",
            "audio-1",
            "Realme RMX3910",
        ).joinToString(AlertLineCodec.SEPARATOR)
        val restored = AlertLineCodec.decode(legacy, ::open)
        assertEquals(alert().copy(failedAttempts = 3), restored)
        assertNull(restored?.silentMinutes)
    }

    // A-33 restored (owner order 2026-11-07): the attempt count is the agreed signal's own number, so
    // it has to survive the offline queue exactly like the photo id and the fix.
    @Test
    fun a_wrong_unlock_attempt_count_survives_the_queue() {
        val stored = AlertLineCodec.encode(
            alert().copy(kind = AlertKind.LOCK, photoEvidenceId = null, failedAttempts = 4),
            ::seal,
        )

        val restored = AlertLineCodec.decode(stored, ::open)

        assertEquals(AlertKind.LOCK, restored?.kind)
        assertEquals(4, restored?.failedAttempts)
    }

    // The removal build wrote this slot as empty on purpose. Reading it back must yield null, because
    // claiming an attempt count that was never written would be a fabricated fact.
    @Test
    fun an_empty_attempt_slot_reads_as_no_count() {
        val stored = AlertLineCodec.encode(alert(), ::seal)

        assertNull(AlertLineCodec.decode(stored, ::open)?.failedAttempts)
    }

    @Test
    fun a_position_that_cannot_be_opened_loses_the_pin_and_keeps_the_alert() {
        val stored = AlertLineCodec.encode(alert(), ::seal)
        val restored = AlertLineCodec.decode(stored, { null })
        assertEquals(alert().copy(location = null), restored)
    }

    @Test
    fun a_device_name_carrying_the_separator_cannot_shift_the_fields() {
        val stored = AlertLineCodec.encode(alert().copy(device = "Realme|RMX3910"), ::seal)
        assertEquals("Realme RMX3910", AlertLineCodec.decode(stored, ::open)?.device)
    }

    @Test
    fun a_line_this_build_cannot_make_sense_of_is_dropped_rather_than_thrown() {
        assertNull(AlertLineCodec.decode("not-a-line", ::open))
        assertNull(AlertLineCodec.decode("x|MOTION|event|1|2|3|4|5|6|7|8|9", ::open))
    }

    @Test
    /** An alert queued by a newer build (a kind this one does not know) is unreadable, not guessed. */
    fun an_unknown_guard_kind_is_not_guessed_at() {
        assertNull(AlertLineCodec.decode("1700000001000|NOT_A_KIND||||0||||x|", ::open))
    }
}
