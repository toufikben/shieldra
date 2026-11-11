package com.shieldra.protection

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The geometry for "safe zones" (amendment A-36, narrowed by A-47 D2): places the owner marks as
 * normal — home, work — where the noisy layer is held quiet. The zone is a silencer, not a trigger:
 * leaving it starts nothing, and every guard outside simply returns to its own base behavior.
 * Evaluated against the per-event and periodic one-shot fixes the protection service already takes —
 * never a continuous GPS stream. Kept pure so the distance maths and the inside/outside decision are
 * unit-tested without a device.
 */
/** A safe zone the owner marked, with a name for the UI and the geometry for the check. */
data class SafeZone(
    val name: String,
    val zone: GeofenceLogic.Zone,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 200.0

        /** The sizes the owner may pick: a flat or house, a block, a neighbourhood, a district. */
        val RADIUS_CHOICES_M: List<Int> = listOf(100, 200, 500, 1000)
    }
}

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
     * Outside every zone even after giving the fix its own error: a fused fix of ±300 m that sits 200 m
     * from the edge may really be inside, so it proves nothing. Only a fix whose whole uncertainty
     * circle clears every zone counts as having left. Used to move the quiet state, never to raise a
     * signal of its own (A-47 D2).
     */
    fun isConfidentlyOutside(lat: Double, lon: Double, accuracyMeters: Double, zones: List<Zone>): Boolean =
        zones.isNotEmpty() && zones.all {
            distanceMeters(lat, lon, it.latitude, it.longitude) - accuracyMeters.coerceAtLeast(0.0) > it.radiusMeters
        }

    /** The state to carry to the next fix: decisively inside, decisively outside, or unchanged if unsure. */
    fun nextInsideState(lat: Double, lon: Double, accuracyMeters: Double, zones: List<Zone>, previous: Boolean): Boolean = when {
        isInsideAny(lat, lon, zones) -> true
        isConfidentlyOutside(lat, lon, accuracyMeters, zones) -> false
        else -> previous
    }
}
