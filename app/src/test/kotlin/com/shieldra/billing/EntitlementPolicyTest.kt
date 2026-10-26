package com.shieldra.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntitlementPolicyTest {

    @Test
    fun `owning Pro is premium whatever else is true`() {
        assertEquals(Tier.PREMIUM, EntitlementPolicy.resolve(ownsPro = true, queryOk = false, installedFromPlay = false, stored = Tier.FREE))
    }

    @Test
    fun `a Play build whose query succeeded and found nothing is downgraded to free`() {
        assertEquals(Tier.FREE, EntitlementPolicy.resolve(ownsPro = false, queryOk = true, installedFromPlay = true, stored = Tier.PREMIUM))
    }

    @Test
    fun `a failed query never switches protection off`() {
        assertEquals(Tier.PREMIUM, EntitlementPolicy.resolve(ownsPro = false, queryOk = false, installedFromPlay = true, stored = Tier.PREMIUM))
    }

    @Test
    fun `a sideloaded build keeps its stored plan - it has no Play to ask`() {
        assertEquals(Tier.PREMIUM, EntitlementPolicy.resolve(ownsPro = false, queryOk = true, installedFromPlay = false, stored = Tier.PREMIUM))
        assertEquals(Tier.FREE, EntitlementPolicy.resolve(ownsPro = false, queryOk = true, installedFromPlay = false, stored = Tier.FREE))
    }
}

class PlanChooserTest {

    private val monthlyBase = RawOffer("pro-monthly", null, "tok-m", listOf(RawPhase(2_990_000, "\$2.99", "P1M")))
    private val yearlyBase = RawOffer("pro-yearly", null, "tok-y", listOf(RawPhase(19_990_000, "\$19.99", "P1Y")))
    private val yearlyTrial = RawOffer(
        "pro-yearly", "trial", "tok-y-trial",
        listOf(RawPhase(0, "Free", "P7D"), RawPhase(19_990_000, "\$19.99", "P1Y")),
    )

    @Test
    fun `yearly leads and prices come from the last (recurring) phase`() {
        val plans = PlanChooser.plans(listOf(monthlyBase, yearlyBase))
        assertEquals(listOf("pro-yearly", "pro-monthly"), plans.map { it.basePlanId })
        assertEquals("\$19.99", plans.first().recurringPrice)
        assertEquals(ProPeriod.YEARLY, plans.first().period)
        assertNull(plans.first().trialDays)
    }

    @Test
    fun `an eligible free trial wins and reports its length and token`() {
        val plans = PlanChooser.plans(listOf(yearlyBase, yearlyTrial, monthlyBase))
        val yearly = plans.first { it.basePlanId == "pro-yearly" }
        assertEquals("tok-y-trial", yearly.offerToken)
        assertEquals(7, yearly.trialDays)
        assertEquals("\$19.99", yearly.recurringPrice)
    }

    @Test
    fun `a plan with no phases is dropped rather than shown with a blank price`() {
        assertEquals(emptyList(), PlanChooser.plans(listOf(RawOffer("pro-monthly", null, "t", emptyList()))))
    }

    @Test
    fun `iso periods map to trial days`() {
        assertEquals(7, PlanChooser.trialDaysOf("P7D"))
        assertEquals(7, PlanChooser.trialDaysOf("P1W"))
        assertEquals(30, PlanChooser.trialDaysOf("P1M"))
        assertNull(PlanChooser.trialDaysOf("P0D"))
        assertNull(PlanChooser.trialDaysOf("nonsense"))
    }

    @Test
    fun `periods are recognised`() {
        assertEquals(ProPeriod.MONTHLY, PlanChooser.periodOf("P1M"))
        assertEquals(ProPeriod.YEARLY, PlanChooser.periodOf("P1Y"))
        assertEquals(ProPeriod.OTHER, PlanChooser.periodOf("P3M"))
    }
}
