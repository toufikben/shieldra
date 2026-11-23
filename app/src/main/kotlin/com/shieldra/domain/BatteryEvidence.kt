package com.shieldra.domain

/**
 * The battery reading taken at the moment of capture, as it is stored with the incident.
 *
 * It lives in Domain because two layers that never meet need the same format: the protection
 * service writes the payload while it is taking evidence, and the event-detail screen parses it
 * later. Duplicating the format on both sides is how the two drift apart, and a reading nobody can
 * parse is worse than no reading at all: it reads as "0% and not charging", which is a claim about
 * a phone nobody measured.
 *
 * The payload is `percent|charging`, and the charging flag is the two-valued string `true`/`false`
 * so it survives a round trip without a locale-sensitive boolean parser.
 */
object BatteryEvidence {
    /** What one stored reading says. */
    data class Reading(val percent: Int, val charging: Boolean)

    /** The evidence kind a battery payload is recorded under. */
    const val KIND = "BATTERY"

    private const val MIN_PERCENT = 0
    private const val MAX_PERCENT = 100
    private const val SEPARATOR = '|'

    /**
     * The payload for one measured reading, or null when the level is not a level. Refusing at the
     * write side keeps an impossible value out of the record instead of letting every later reader
     * decide what to do with it.
     */
    fun encode(percent: Int, charging: Boolean): String? {
        if (percent !in MIN_PERCENT..MAX_PERCENT) return null
        return "$percent$SEPARATOR$charging"
    }

    /**
     * The reading a stored payload carries, or null when this build cannot read it. A payload that
     * is absent, truncated, or names a level outside the battery range yields no reading.
     */
    fun decode(payload: String?): Reading? {
        val parts = payload?.split(SEPARATOR) ?: return null
        if (parts.size != 2) return null
        val percent = parts[0].toIntOrNull() ?: return null
        if (percent !in MIN_PERCENT..MAX_PERCENT) return null
        val charging = when (parts[1]) {
            "true" -> true
            "false" -> false
            else -> return null
        }
        return Reading(percent = percent, charging = charging)
    }
}
