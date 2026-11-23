package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P2-4: every setting that makes the phone less protected asks for the owner's screen lock.
 *
 * The list used to cover only the on/off switches, so a thief holding an unlocked phone could keep
 * every guard "on" while making them useless: a shorter audio clip, a wider escalation threshold, a
 * lazier tracking interval, a lower battery level. Each case below is one of those quiet weakenings.
 */
class ProtectionChangeWeakeningTest {
    private val armed = ProtectionSettings(armed = true)

    @Test
    fun a_shorter_audio_clip_saves_less_evidence() {
        assertTrue(ProtectionChange.weakens(armed.copy(audioSeconds = 20), armed.copy(audioSeconds = 5)))
    }

    @Test
    fun a_wider_escalation_threshold_needs_more_before_the_siren() {
        assertTrue(
            ProtectionChange.weakens(
                armed.copy(escalationThreshold = EscalationPolicy.DEFAULT_THRESHOLD),
                armed.copy(escalationThreshold = EscalationPolicy.MAX_THRESHOLD),
            ),
        )
    }

    @Test
    fun a_lazier_tracking_interval_weakens_the_trail() {
        assertTrue(ProtectionChange.weakens(armed.copy(trackingIntervalMinutes = 2), armed.copy(trackingIntervalMinutes = 10)))
    }

    @Test
    fun a_shorter_tracking_duration_weakens_the_trail() {
        assertTrue(ProtectionChange.weakens(armed.copy(trackingDurationMinutes = 30), armed.copy(trackingDurationMinutes = 5)))
    }

    @Test
    fun a_lower_battery_level_announces_the_last_breath_later() {
        // The threshold is the level at which the last-breath capture fires: 5% is later than 10%,
        // and by then the phone may be already dead.
        assertTrue(
            ProtectionChange.weakens(
                armed.copy(batteryThreshold = BatteryBreathLogic.DEFAULT_LEVEL),
                armed.copy(batteryThreshold = BatteryBreathLogic.MIN_LEVEL),
            ),
        )
    }

    @Test
    fun a_longer_motion_grace_delays_the_response() {
        assertTrue(ProtectionChange.weakens(armed.copy(motionGraceSeconds = 5), armed.copy(motionGraceSeconds = 60)))
    }

    @Test
    fun a_lazier_safe_zone_sampler_notices_leaving_late() {
        assertTrue(ProtectionChange.weakens(armed.copy(geofenceIntervalMinutes = 3), armed.copy(geofenceIntervalMinutes = 30)))
    }

    @Test
    fun clearing_the_owner_contact_leaves_nobody_to_answer_the_lost_notice() {
        assertTrue(ProtectionChange.weakens(armed.copy(ownerContact = "+213555000000"), armed.copy(ownerContact = "")))
    }

    @Test
    fun a_cheaper_plan_takes_capabilities_away() {
        assertTrue(
            ProtectionChange.weakens(
                armed.copy(tier = com.shieldra.billing.Tier.PREMIUM),
                armed.copy(tier = com.shieldra.billing.Tier.FREE),
            ),
        )
    }

    @Test
    fun making_the_phone_more_protected_asks_for_nothing() {
        assertFalse(ProtectionChange.weakens(armed.copy(audioSeconds = 5), armed.copy(audioSeconds = 30)))
        assertFalse(ProtectionChange.weakens(armed.copy(trackingIntervalMinutes = 10), armed.copy(trackingIntervalMinutes = 2)))
        assertFalse(ProtectionChange.weakens(armed.copy(batteryThreshold = 5), armed.copy(batteryThreshold = 20)))
        assertFalse(ProtectionChange.weakens(armed.copy(ownerContact = ""), armed.copy(ownerContact = "+213555000000")))
        assertFalse(ProtectionChange.weakens(armed.copy(audioEnabled = false), armed.copy(audioEnabled = true)))
    }

    @Test
    fun an_unrelated_edit_is_not_a_weakening() {
        assertFalse(ProtectionChange.weakens(armed.copy(lostMessage = "old"), armed.copy(lostMessage = "new")))
    }
}
