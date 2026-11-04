package com.shieldra.protection

/** What the ladder is allowed to do given where the phone is. */
enum class ZoneResponse {
    /** The full ladder: lock, demand, bursts, repeats. */
    FULL,

    /** Lock and send the evidence, but no siren and no repeating burst train. */
    QUIET,

    /** Record the event and do nothing else. Only inside a zone and plugged in. */
    MUTED,
}

/**
 * The safe-zone decision (design D2).
 *
 * A zone is a statement about *who is near the phone*, not about whether it can be stolen: a phone
 * taken off a bedside table is taken inside a zone, and a thief who grabs a charging phone from a night
 * stand is exactly the case the owner asked to keep quiet about, because neighbours should not be woken
 * for it. So detection is never muted and the only fully silent case is inside-and-charging, where the
 * phone is where it sleeps.
 *
 * Unknown fails closed. A missing permission, an old sample or a failed fix must not turn the guard off
 * — the owner chose the zones, but never chose "no protection when the phone cannot see itself".
 */
object ZonePolicy {

    fun responseFor(
        zonesExist: Boolean,
        stateKnown: Boolean,
        inside: Boolean,
        charging: Boolean,
    ): ZoneResponse = when {
        !zonesExist || !stateKnown || !inside -> ZoneResponse.FULL
        charging -> ZoneResponse.MUTED
        else -> ZoneResponse.QUIET
    }
}
