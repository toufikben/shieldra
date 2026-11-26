package com.shieldra.protection

/**
 * Whether the phone should stay discreet right now (owner decision 2026-11-08, restoring the safe-zone
 * quieting that design B had removed — but only the noisy layer, never the evidence).
 *
 * Inside a place the owner marked safe (home, work) the loud and periodic layer is nuisance: the
 * siren, the countdown signal and the Motion alert. Those go quiet. Detection and evidence do not:
 * a Motion still records its event and stores its photo/fix, and the wrong-unlock, SIM, battery and
 * leave-a-zone alerts are never touched by this policy — they are evidence, not noise.
 *
 * Fail-closed by construction: quiet needs the zone guard on, at least one zone to be inside of, a
 * reading that is still fresh, and that reading saying inside. No reading, a stale one, a clock that
 * jumped, no zones left, or being outside all mean full strength — so a thief cannot silence the phone
 * by holding an old reading, and a removed zone cannot keep the phone quiet by accident.
 *
 * Pure and Android-free so the rule that decides discretion is testable on the JVM.
 */
object SafeZoneQuietPolicy {

    fun isQuiet(
        geofenceEnabled: Boolean,
        hasZones: Boolean,
        readingFresh: Boolean,
        inside: Boolean,
    ): Boolean = geofenceEnabled && hasZones && readingFresh && inside
}
