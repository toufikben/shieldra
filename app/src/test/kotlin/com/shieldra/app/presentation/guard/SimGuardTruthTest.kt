package com.shieldra.app.presentation.guard

import com.shieldra.app.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P2-6: what the SIM guard may and may not claim, including the eSIM case.
 *
 * `docs/reports/batch-I-sim-feasibility.md:71-79` is the binding measurement: a store-distributed app
 * cannot read the ICCID (`READ_PRIVILEGED_PHONE_STATE`), so it cannot compare the card that is in the
 * phone with the card that was there before. A swap to a different card is therefore never *proven*;
 * what the guard really sees is the armed subscription disappearing, a subscription that was not there
 * at arming appearing, or the radio going dark. The screen used to say "pulled or swapped", which is a
 * claim the platform does not let this build make.
 *
 * The embedded case is the same rule with one more caveat: an eSIM profile is a subscription like any
 * other from the app's side (`SubscriptionInfo`), so enabling or deleting a profile looks exactly like
 * a card change, and the app has no way to tell an embedded profile from a physical one that matters to
 * the owner. The wording says so instead of leaving the owner to discover it after a false alarm.
 */
class SimGuardTruthTest {

    @Test
    fun p2_6_a_swap_is_never_claimed_because_the_card_identity_is_unreachable() {
        assertFalse(SimGuardTruth.canProveSwap, "ICCID is privileged-only, so a swap cannot be proven")
        assertFalse(SimGuardTruth.canTellEmbeddedApart, "SubscriptionInfo does not separate eSIM for us")
    }

    @Test
    fun p2_6_what_the_guard_really_detects_is_named() {
        assertTrue(SimGuardTruth.canDetectRemoval)
        assertTrue(SimGuardTruth.canDetectForeignSubscription)
        assertTrue(SimGuardTruth.canDetectNetworkLoss)
    }

    @Test
    fun p2_6_the_guard_row_states_the_detection_it_can_deliver() {
        assertEquals(R.string.protection_guard_sim_desc, SimGuardTruth.descriptionRes)
    }

    @Test
    fun p2_6_the_embedded_profile_caveat_has_its_own_sentence() {
        assertEquals(R.string.protection_guard_sim_esim_note, SimGuardTruth.embeddedNoteRes)
    }
}
