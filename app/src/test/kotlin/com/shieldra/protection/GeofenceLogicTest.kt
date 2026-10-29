package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeofenceLogicTest {

    private val home = GeofenceLogic.Zone(latitude = 36.0, longitude = 3.0, radiusMeters = 1_000.0)

    @Test
    fun `distance from a point to itself is zero`() {
        assertEquals(0.0, GeofenceLogic.distanceMeters(36.0, 3.0, 36.0, 3.0), 0.0001)
    }

    @Test
    fun `a tenth of a degree of latitude is about eleven kilometres`() {
        val d = GeofenceLogic.distanceMeters(36.0, 3.0, 36.1, 3.0)
        assertTrue(d in 11_000.0..11_200.0, "expected ~11.1km, was $d")
    }

    @Test
    fun `a point inside the radius is inside the zone`() {
        assertTrue(GeofenceLogic.isInside(36.005, 3.0, home))
    }

    @Test
    fun `a point beyond the radius is outside the zone`() {
        assertFalse(GeofenceLogic.isInside(36.1, 3.0, home))
    }

    @Test
    fun `with no zones nothing is inside and nothing has left`() {
        assertFalse(GeofenceLogic.isInsideAny(36.0, 3.0, emptyList()))
        assertFalse(GeofenceLogic.hasLeftSafeZone(36.1, 3.0, emptyList(), wasInside = true))
    }

    @Test
    fun `leaving fires only on the crossing out`() {
        val zones = listOf(home)
        assertTrue(GeofenceLogic.hasLeftSafeZone(36.1, 3.0, zones, wasInside = true))
        assertFalse(GeofenceLogic.hasLeftSafeZone(36.1, 3.0, zones, wasInside = false))
        assertFalse(GeofenceLogic.hasLeftSafeZone(36.005, 3.0, zones, wasInside = true))
    }
}

class GeofenceAccuracyTest {
    private val home = GeofenceLogic.Zone(36.0, 3.0, 200.0)

    // ~500 m north of home: 0.0045 deg latitude is about 500 m.
    private val farLat = 36.0045

    @Test
    fun `a precise fix well outside counts as having left`() {
        assertTrue(GeofenceLogic.hasLeftSafeZone(farLat, 3.0, 20.0, listOf(home), wasInside = true))
    }

    @Test
    fun `a vague fix that could still be inside does not fire`() {
        assertFalse(GeofenceLogic.hasLeftSafeZone(farLat, 3.0, 400.0, listOf(home), wasInside = true))
    }

    @Test
    fun `an ambiguous fix leaves the previous state alone`() {
        assertTrue(GeofenceLogic.nextInsideState(farLat, 3.0, 400.0, listOf(home), previous = true))
        assertFalse(GeofenceLogic.nextInsideState(farLat, 3.0, 400.0, listOf(home), previous = false))
    }

    @Test
    fun `a decisive fix sets the state`() {
        assertFalse(GeofenceLogic.nextInsideState(farLat, 3.0, 20.0, listOf(home), previous = true))
        assertTrue(GeofenceLogic.nextInsideState(36.0, 3.0, 20.0, listOf(home), previous = false))
    }

    @Test
    fun `no zones never fires`() {
        assertFalse(GeofenceLogic.hasLeftSafeZone(farLat, 3.0, 5.0, emptyList(), wasInside = true))
    }
}
