package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

/** A-33: the SIM/network guard that tells a swap, a pull and a cut network apart from normal use. */
class SimGuardLogicTest {
    private val logic = SimGuardLogic(baseline = setOf("sim-owner"))

    @Test
    fun a_foreign_sim_is_reported_even_if_the_owner_sim_is_still_there() {
        val v = logic.classify(SimSnapshot(simIds = setOf("sim-owner", "sim-thief"), airplaneOn = false, hasService = true))
        assertEquals(SimGuardLogic.Verdict.ForeignInserted, v)
    }

    @Test
    fun pulling_the_only_sim_is_a_removal() {
        val v = logic.classify(SimSnapshot(simIds = emptySet(), airplaneOn = false, hasService = false))
        assertEquals(SimGuardLogic.Verdict.Removed, v)
    }

    @Test
    fun airplane_mode_with_the_sim_still_in_is_a_network_loss() {
        val v = logic.classify(SimSnapshot(simIds = setOf("sim-owner"), airplaneOn = true, hasService = false))
        assertEquals(SimGuardLogic.Verdict.NetworkLost, v)
    }

    @Test
    fun losing_service_in_a_tunnel_is_a_network_loss_not_a_theft() {
        val v = logic.classify(SimSnapshot(simIds = setOf("sim-owner"), airplaneOn = false, hasService = false))
        assertEquals(SimGuardLogic.Verdict.NetworkLost, v)
    }

    @Test
    fun the_armed_sim_with_service_is_normal() {
        val v = logic.classify(SimSnapshot(simIds = setOf("sim-owner"), airplaneOn = false, hasService = true))
        assertEquals(SimGuardLogic.Verdict.Normal, v)
    }

    /**
     * A card-less phone armed on purpose still gets a guard. The baseline is recorded as empty when the
     * readable answer was "no SIM in this phone" (§5.11), and the only reading of that which protects
     * the owner is to treat any card that appears later as somebody else's.
     */
    @Test
    fun a_card_less_baseline_reports_the_card_that_shows_up_later_as_foreign() {
        val cardLess = SimGuardLogic(baseline = emptySet())

        assertEquals(
            SimGuardLogic.Verdict.ForeignInserted,
            cardLess.classify(SimSnapshot(simIds = setOf("sim-thief"), airplaneOn = false, hasService = true)),
        )
    }

    @Test
    fun an_armed_card_less_phone_without_a_card_stays_calm() {
        val cardLess = SimGuardLogic(baseline = emptySet())

        assertEquals(
            SimGuardLogic.Verdict.Normal,
            cardLess.classify(SimSnapshot(simIds = emptySet(), airplaneOn = false, hasService = false)),
        )
    }

    @Test
    fun a_card_less_phone_still_reports_airplane_mode() {
        val cardLess = SimGuardLogic(baseline = emptySet())

        assertEquals(
            SimGuardLogic.Verdict.NetworkLost,
            cardLess.classify(SimSnapshot(simIds = emptySet(), airplaneOn = true, hasService = false)),
        )
    }
}
