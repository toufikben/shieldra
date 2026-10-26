package com.shieldra.billing

/**
 * Which plan the phone is on, decided from what Google Play really answered (amendment A-40).
 *
 * The rule that keeps the owner's own phone usable: only a build installed from Google Play, whose
 * purchase query succeeded and found no active Pro subscription, is downgraded to Free. A sideloaded
 * test build (no Play to ask) or a failed query keeps whatever was stored — so a network blip never
 * switches a paying owner's protection off, and a debug build never locks itself.
 *
 * Pure: facts in, tier out. Unit-tested.
 */
object EntitlementPolicy {
    fun resolve(ownsPro: Boolean, queryOk: Boolean, installedFromPlay: Boolean, stored: Tier): Tier = when {
        ownsPro -> Tier.PREMIUM
        queryOk && installedFromPlay -> Tier.FREE
        else -> stored
    }
}

/** A subscription offer as Google Play reports it, reduced to what the plan list needs. */
data class RawPhase(val priceMicros: Long, val formattedPrice: String, val billingPeriod: String)

data class RawOffer(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val phases: List<RawPhase>,
)

/** Turns Play's offers into the plans the screen sells. Pure and unit-tested. */
object PlanChooser {

    fun plans(offers: List<RawOffer>): List<ProPlan> = offers
        .groupBy { it.basePlanId }
        .mapNotNull { (basePlanId, group) ->
            // Play only lists offers this user is eligible for, so a trial offer in the list is a real trial.
            val trial = group.firstOrNull { it.phases.size > 1 && it.phases.first().priceMicros == 0L }
            val chosen = trial ?: group.firstOrNull { it.offerId == null } ?: group.first()
            val recurring = chosen.phases.lastOrNull() ?: return@mapNotNull null
            ProPlan(
                basePlanId = basePlanId,
                period = periodOf(recurring.billingPeriod),
                offerToken = chosen.offerToken,
                recurringPrice = recurring.formattedPrice,
                trialDays = if (chosen === trial) trialDaysOf(chosen.phases.first().billingPeriod) else null,
            )
        }
        // The yearly plan leads: it is the better value and the one to show first.
        .sortedBy { if (it.period == ProPeriod.YEARLY) 0 else if (it.period == ProPeriod.MONTHLY) 1 else 2 }

    fun periodOf(iso: String): ProPeriod = when (iso) {
        "P1M" -> ProPeriod.MONTHLY
        "P1Y", "P12M" -> ProPeriod.YEARLY
        else -> ProPeriod.OTHER
    }

    private val ISO_PERIOD = Regex("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?$")

    /** "P7D" → 7, "P1W" → 7, "P1M" → 30; null for anything that is not a positive period. */
    fun trialDaysOf(iso: String): Int? {
        val m = ISO_PERIOD.matchEntire(iso) ?: return null
        fun part(i: Int) = m.groupValues[i].toIntOrNull() ?: 0
        val days = part(1) * 365 + part(2) * 30 + part(3) * 7 + part(4)
        return days.takeIf { it > 0 }
    }
}
