package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The safe-zone quieting is fail-closed: only a fresh "inside" reading, with the guard on and at
 * least one zone to be inside of, makes the phone discreet. Every other combination keeps full
 * strength (owner decision 2026-11-08), so a removed zone, a dead sampler or a jumped clock can never
 * silence the phone.
 */
class SafeZoneQuietPolicyTest {

    @Test
    fun `fresh inside with the guard on and a zone defined is quiet`() {
        assertTrue(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = true,
                hasZones = true,
                readingFresh = true,
                inside = true,
            ),
        )
    }

    @Test
    fun `the guard off is never quiet even inside a fresh zone`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = false,
                hasZones = true,
                readingFresh = true,
                inside = true,
            ),
        )
    }

    @Test
    fun `a stale reading is never quiet`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = true,
                hasZones = true,
                readingFresh = false,
                inside = true,
            ),
        )
    }

    @Test
    fun `outside every zone is never quiet`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = true,
                hasZones = true,
                readingFresh = true,
                inside = false,
            ),
        )
    }

    @Test
    fun `no zones left is never quiet even with a fresh inside reading`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = true,
                hasZones = false,
                readingFresh = true,
                inside = true,
            ),
        )
    }

    @Test
    fun `no fresh reading and outside stays at full strength`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = true,
                hasZones = true,
                readingFresh = false,
                inside = false,
            ),
        )
    }

    @Test
    fun `guard off and no zones and stale and outside stays at full strength`() {
        assertFalse(
            SafeZoneQuietPolicy.isQuiet(
                geofenceEnabled = false,
                hasZones = false,
                readingFresh = false,
                inside = false,
            ),
        )
    }

    @Test
    fun `only one of the sixteen combinations is quiet`() {
        var quietCount = 0
        for (enabled in listOf(false, true)) {
            for (hasZones in listOf(false, true)) {
                for (fresh in listOf(false, true)) {
                    for (inside in listOf(false, true)) {
                        if (SafeZoneQuietPolicy.isQuiet(enabled, hasZones, fresh, inside)) quietCount++
                    }
                }
            }
        }
        assertTrue(quietCount == 1, "exactly the enabled+zones+fresh+inside corner is quiet")
    }
}
