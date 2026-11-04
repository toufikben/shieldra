package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A-45 D2: a safe zone mutes escalation, never detection, and an unknown zone fails closed. The most
 * likely snatch is a phone taken off a bedside table — inside the zone — so silence there would be the
 * whole feature turned off. What is muted is the noise aimed at the neighbourhood, not the evidence.
 */
class ZonePolicyTest {

    @Test
    fun `no zones at all means the full ladder`() {
        assertEquals(
            ZoneResponse.FULL,
            ZonePolicy.responseFor(zonesExist = false, stateKnown = true, inside = true, charging = false),
        )
    }

    @Test
    fun `an unknown zone state fails closed to the full ladder`() {
        assertEquals(
            ZoneResponse.FULL,
            ZonePolicy.responseFor(zonesExist = true, stateKnown = false, inside = true, charging = true),
        )
    }

    @Test
    fun `outside a zone is the full ladder even while charging`() {
        assertEquals(
            ZoneResponse.FULL,
            ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = false, charging = true),
        )
    }

    @Test
    fun `inside a zone and charging is the only fully quiet case`() {
        assertEquals(
            ZoneResponse.MUTED,
            ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = true, charging = true),
        )
    }

    @Test
    fun `inside a zone and unplugged still locks and still sends evidence`() {
        assertEquals(
            ZoneResponse.QUIET,
            ZonePolicy.responseFor(zonesExist = true, stateKnown = true, inside = true, charging = false),
        )
    }

    @Test
    fun `a stale sample is not a fresh one and a fresh one is not stale`() {
        val sample = ZoneSample(inside = true, atMs = 1_000_000L)
        assertTrue(sample.isFresh(nowMs = 1_000_001L, staleAfterMs = 60_000L))
        assertFalse(sample.isFresh(nowMs = 1_060_001L, staleAfterMs = 60_000L))
    }

    @Test
    fun `a clock that jumped backwards does not make a stale sample look fresh`() {
        val sample = ZoneSample(inside = true, atMs = 1_000_000L)
        assertFalse(sample.isFresh(nowMs = 900_000L, staleAfterMs = 60_000L))
    }
}
