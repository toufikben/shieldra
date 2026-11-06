package com.shieldra.protection

/**
 * Default labels for safe zones (G-11). A new zone used to be called `Zone ${size + 1}`, which
 * repeats after a deletion: with zones 1 and 2, deleting 1 and adding again produced a second
 * "Zone 2". The Map screen keys zones by name, so a repeated name made one row show the wrong
 * radius and one delete remove both places. The smallest unused number never repeats by
 * construction. Kept pure so the rule is tested without a device.
 */
object SafeZoneLabels {
    private val NUMBERED = Regex("""^Zone (\d+)$""")

    fun nextDefaultName(existing: List<SafeZone>): String {
        val used = existing.mapNotNull { zone ->
            NUMBERED.matchEntire(zone.name)?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        var number = 1
        while (number in used) number++
        return "Zone $number"
    }
}
