package com.shieldra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * P1-5: the battery reading taken at capture time is stored with the incident and read back later.
 *
 * The two sides are separate processes' worth of code apart - the protection service writes the
 * payload, the event-detail screen parses it - so the format lives in one pure place and both sides
 * use it. A payload this build cannot read must read as "no reading", never as zero percent or as
 * "not charging", because those are claims about a phone nobody measured.
 */
class BatteryEvidenceTest {
    @Test
    fun a_reading_round_trips_through_the_stored_payload() {
        val encoded = BatteryEvidence.encode(percent = 84, charging = true)

        assertEquals(BatteryEvidence.Reading(percent = 84, charging = true), BatteryEvidence.decode(encoded))
    }

    @Test
    fun a_discharging_reading_keeps_its_false_charging_flag() {
        val encoded = BatteryEvidence.encode(percent = 7, charging = false)

        val decoded = BatteryEvidence.decode(encoded)
        assertEquals(7, decoded?.percent)
        assertEquals(false, decoded?.charging)
    }

    @Test
    fun a_payload_this_build_cannot_read_reads_as_no_reading() {
        assertNull(BatteryEvidence.decode("not a battery payload"))
        assertNull(BatteryEvidence.decode("84"))
        assertNull(BatteryEvidence.decode("84|maybe"))
        assertNull(BatteryEvidence.decode("|true"))
        assertNull(BatteryEvidence.decode("101|true"), "a level outside 0..100 is not a measurement")
    }

    @Test
    fun a_level_outside_the_battery_range_is_refused_at_the_write_side() {
        assertNull(BatteryEvidence.encode(percent = -1, charging = true))
        assertNull(BatteryEvidence.encode(percent = 101, charging = false))
    }
}
