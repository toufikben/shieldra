package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

/** A-33: the low-battery "last breath" rule — one alert per level per discharge. */
class BatteryBreathLogicTest {
    @Test
    fun the_main_level_fires_once_then_stays_quiet_while_hovering() {
        val logic = BatteryBreathLogic(mainThreshold = { 10 })

        assertEquals(BatteryBreathLogic.Verdict.Breath(10), logic.onLevel(10, charging = false))
        assertEquals(BatteryBreathLogic.Verdict.Nothing, logic.onLevel(9, charging = false))
        assertEquals(BatteryBreathLogic.Verdict.Nothing, logic.onLevel(10, charging = false))
    }

    @Test
    fun the_critical_level_fires_below_three_percent() {
        val logic = BatteryBreathLogic(mainThreshold = { 10 })
        logic.onLevel(10, charging = false)

        assertEquals(BatteryBreathLogic.Verdict.Breath(3), logic.onLevel(3, charging = false))
    }

    @Test
    fun a_drop_straight_to_two_percent_sends_only_the_critical_alert() {
        val logic = BatteryBreathLogic(mainThreshold = { 10 })

        assertEquals(BatteryBreathLogic.Verdict.Breath(3), logic.onLevel(2, charging = false))
        assertEquals(BatteryBreathLogic.Verdict.Nothing, logic.onLevel(2, charging = false))
    }

    @Test
    fun charging_resets_so_the_next_discharge_alerts_again() {
        val logic = BatteryBreathLogic(mainThreshold = { 10 })
        logic.onLevel(8, charging = false)
        assertEquals(BatteryBreathLogic.Verdict.Nothing, logic.onLevel(50, charging = true))

        assertEquals(BatteryBreathLogic.Verdict.Breath(10), logic.onLevel(9, charging = false))
    }

    @Test
    fun climbing_back_above_the_level_resets_without_a_charger() {
        val logic = BatteryBreathLogic(mainThreshold = { 10 })
        logic.onLevel(10, charging = false)
        // Jumps to 16 (> 10 + 5 hysteresis): the discharge is considered recovered.
        logic.onLevel(16, charging = false)

        assertEquals(BatteryBreathLogic.Verdict.Breath(10), logic.onLevel(10, charging = false))
    }
}
