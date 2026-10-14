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
}
