package com.shieldra.delivery

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The line an queued alert carries its position in (audit B-2/§5.13). Two things are settled here and
 * neither can be tested through the Android store: the shape inside the seal has to survive a round
 * trip, and it must never contain the character the alert queue splits its fields on — a position that
 * looked like a field break would damage the alert next to it, not just itself.
 */
class AlertLocationLineTest {

    private val fix = AlertLocation(
        latitude = 36.7538,
        longitude = 3.0588,
        accuracyMeters = 12.5f,
        fixedAt = Instant.ofEpochMilli(1_730_000_000_000L),
        provider = "fused",
    )

    @Test
    fun a_position_survives_being_written_down_and_read_back() {
        assertEquals(fix, AlertLocationLine.parse(AlertLocationLine.format(fix)))
    }

    @Test
    fun a_fix_without_a_reported_radius_reads_back_as_no_radius_rather_than_as_a_guess() {
        val noAccuracy = fix.copy(accuracyMeters = null)

        val read = AlertLocationLine.parse(AlertLocationLine.format(noAccuracy))

        assertEquals(noAccuracy, read)
        assertNull(read?.accuracyMeters)
    }

    @Test
    fun the_plain_spelling_an_older_build_wrote_is_still_read() {
        // Builds before the position was sealed queued this exact shape; those alerts were already
        // waiting on a phone that could not send them, and an update must not throw them away.
        val legacy = "36.7538;3.0588;12.5;1730000000000;fused"

        assertEquals(fix, AlertLocationLine.parse(legacy))
    }

    @Test
    fun a_line_that_is_not_a_position_is_dropped_instead_of_being_guessed_at() {
        assertNull(AlertLocationLine.parse(""))
        assertNull(AlertLocationLine.parse("36.7538"))
        assertNull(AlertLocationLine.parse("36.7538;not-a-latitude;12.5;1730000000000;fused"))
        assertNull(AlertLocationLine.parse("36.7538;3.0588;12.5;later;fused"))
        assertNull(AlertLocationLine.parse("36.7538;3.0588;12.5;1730000000000"))
    }

    @Test
    fun a_position_never_holds_the_character_the_alert_queue_splits_on() {
        val awkward = fix.copy(provider = "fused|network")

        val line = AlertLocationLine.format(awkward)

        assertFalse(line.contains("|"), "a position must not break the alert line it is stored in")
        assertEquals(awkward.copy(provider = "fused network"), AlertLocationLine.parse(line))
    }
}
