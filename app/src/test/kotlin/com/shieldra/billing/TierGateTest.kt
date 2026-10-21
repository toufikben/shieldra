package com.shieldra.billing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The owner's free/paid split: free = location by email only; paid = everything. */
class TierGateTest {
    @Test
    fun the_free_plan_sends_location_by_email_and_nothing_else() {
        val free = Tier.FREE
        assertTrue(TierGate.allowsEmail(free))
        assertTrue(TierGate.allowsLocation(free))
        assertFalse(TierGate.allowsPhoto(free))
        assertFalse(TierGate.allowsBackCamera(free))
        assertFalse(TierGate.allowsAudio(free))
        assertFalse(TierGate.allowsTelegram(free))
        assertFalse(TierGate.allowsCountdown(free))
        assertFalse(TierGate.allowsTracking(free))
        assertFalse(TierGate.allowsRemote(free))
    }

    @Test
    fun the_paid_plan_unlocks_everything() {
        val paid = Tier.PREMIUM
        listOf(
            TierGate.allowsEmail(paid), TierGate.allowsLocation(paid), TierGate.allowsPhoto(paid),
            TierGate.allowsBackCamera(paid), TierGate.allowsAudio(paid), TierGate.allowsTelegram(paid),
            TierGate.allowsCountdown(paid), TierGate.allowsTracking(paid), TierGate.allowsRemote(paid),
        ).forEach { assertTrue(it) }
    }
}
