package com.shieldra.protection

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The geometry for "safe zones" (amendment A-36): places the owner marks as normal — home, work —
 * where movement should not alarm. The phone alerts when it leaves every safe zone while armed, so a
 * thief carrying it away is caught the moment it crosses out. Evaluated against the per-event and
 * periodic one-shot fixes the protection service already takes — never a continuous GPS stream.
 * Kept pure so the distance maths and the inside/outside decision are unit-tested without a device.
 */
object GeofenceLogic {

    data class Zone(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
    )

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two WGS84 points, in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    fun isInside(lat: Double, lon: Double, zone: Zone): Boolean =
        distanceMeters(lat, lon, zone.latitude, zone.longitude) <= zone.radiusMeters

    /** Inside at least one safe zone. With no zones defined there is nothing to be outside of. */
    fun isInsideAny(lat: Double, lon: Double, zones: List<Zone>): Boolean =
        zones.any { isInside(lat, lon, it) }

    /**
     * True only on the crossing OUT: the owner was inside a safe zone and this fix is outside them
     * all. [wasInside] is the previous fix's verdict; the caller keeps it between fixes. Never fires
     * when no zones are defined.
     */
    fun hasLeftSafeZone(lat: Double, lon: Double, zones: List<Zone>, wasInside: Boolean): Boolean =
        zones.isNotEmpty() && wasInside && !isInsideAny(lat, lon, zones)
}
