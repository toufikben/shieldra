package com.shieldra.app.presentation.guard

import com.shieldra.app.R

/**
 * P2-6: what the SIM guard may claim, and the one caveat the owner must read before trusting it.
 *
 * The measurement that binds this file is `docs/reports/batch-I-sim-feasibility.md:71-79`: the card's
 * ICCID is behind `READ_PRIVILEGED_PHONE_STATE`, a privileged permission no store-distributed app can
 * hold, so **"the SIM was swapped to a different card" cannot be proven by this build**. What the guard
 * really observes is the armed subscription disappearing, a subscription that was absent at arming
 * appearing, or the radio going dark — three facts `SimGuardLogic` classifies one by one.
 *
 * The embedded caveat has the same root: from `SubscriptionManager` an eSIM profile is a subscription
 * like any other, so enabling a profile, deleting one, or receiving a carrier push looks exactly like a
 * card being exchanged. The owner is told that in the guard row instead of meeting it as a false alarm.
 *
 * Keeping the booleans here rather than inline in the composable is what makes the promise checkable
 * without a device: the test asserts the two "cannot" facts, and the strings are the only place the
 * sentences live.
 */
object SimGuardTruth {
    /** A card that was there at arming is gone; this is the one identity-free fact the guard sees. */
    const val canDetectRemoval: Boolean = true

    /** A subscription that was not present at arming is now present. */
    const val canDetectForeignSubscription: Boolean = true

    /** Airplane mode or no service: a thief cutting the radio looks like this. */
    const val canDetectNetworkLoss: Boolean = true

    /**
     * False on purpose. Without the ICCID the app can see *that* the set of subscriptions changed, not
     * *which card* replaced which — the same subscription id is reused across physical cards, and a
     * profile swap on a dual-SIM phone can leave the id untouched.
     */
    const val canProveSwap: Boolean = false

    /** False on purpose: `SubscriptionInfo` does not tell this build that a subscription is embedded. */
    const val canTellEmbeddedApart: Boolean = false

    @get:androidx.annotation.StringRes
    val descriptionRes: Int = R.string.protection_guard_sim_desc

    @get:androidx.annotation.StringRes
    val embeddedNoteRes: Int = R.string.protection_guard_sim_esim_note
}
