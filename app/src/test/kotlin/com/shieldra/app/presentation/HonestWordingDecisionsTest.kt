package com.shieldra.app.presentation

import com.shieldra.app.R
import com.shieldra.app.presentation.guard.AgeParts
import com.shieldra.app.presentation.guard.ageQuantityRes
import com.shieldra.app.presentation.guard.eventStateLabelRes
import com.shieldra.app.presentation.guard.toDeliveryStatus
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.NetworkStatus
import com.shieldra.app.presentation.guard.deliveryChipLabelRes
import com.shieldra.app.presentation.guard.networkSlotLabelRes
import com.shieldra.app.presentation.settings.SettingsContent
import com.shieldra.app.presentation.settings.SettingsSubtitleKind
import com.shieldra.app.presentation.settings.appLockSubtitleRes
import com.shieldra.app.presentation.settings.retentionSubtitleRes
import com.shieldra.domain.EventState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Q-K-4 (amendment A-26): the four labels the owner's phone measured, plus the age wording. Each
 * finding is a sentence the build could not prove, so each is pinned here as a decision made
 * outside Compose — which wording a state is allowed to reach. The exact sentences live in the two
 * locale files, and the static gate (`g4b-review-check.sh`, §22) reads them from there.
 *
 * Nothing in this file changes a Keystore, crypto, permission or retention decision: it only stops
 * the interface from saying more than those decisions already do.
 */
class HonestWordingDecisionsTest {

    // F-K9 — the screen said "Require authentication" above a key policy that requires none.
    @Test
    fun qk4_the_app_lock_row_stops_claiming_an_authentication_the_key_policy_does_not_require() {
        val row = SettingsContent.rows.single { it.id == "app_lock" }

        assertEquals(SettingsSubtitleKind.AppLockNotEnforced, row.subtitle)
        assertFalse(row.requiresAuth, "Nothing in this build authenticates before opening a row")
        assertEquals(R.string.settings_app_lock_not_enforced, appLockSubtitleRes())
    }

    // F-K6 — "Policy pending" understates the build the owner approved on 2026-10-02 (A-21).
    @Test
    fun qk4_the_retention_row_states_the_windows_that_actually_run() {
        val row = SettingsContent.rows.single { it.id == "retention" }

        assertEquals(SettingsSubtitleKind.RetentionWindows, row.subtitle)
        assertFalse(row.requiresAuth, "Nothing in this build authenticates before opening a row")
        assertEquals(R.string.settings_retention_windows, retentionSubtitleRes(storageInstalled = true))
    }

    @Test
    fun qk4_the_retention_row_promises_nothing_while_nothing_is_stored() {
        assertEquals(R.string.settings_retention_no_data, retentionSubtitleRes(storageInstalled = false))
    }

    // F-K7 — an unmeasured network state was rendered as the fact "Offline".
    @Test
    fun qk4_an_unmeasured_network_state_never_reaches_the_offline_wording() {
        assertNull(networkSlotLabelRes(NetworkStatus.Unknown), "Unknown must show the caller's unknown label")
        assertNull(networkSlotLabelRes(NetworkStatus.Online), "A connected reading shows the label it measured")
        assertEquals(
            R.string.status_network_offline,
            networkSlotLabelRes(NetworkStatus.Offline),
            "Only a real offline measurement may say Offline",
        )
    }

    @Test
    fun qk4_the_three_network_states_are_the_only_thing_the_slot_can_report() {
        assertEquals(
            setOf(NetworkStatus.Unknown, NetworkStatus.Offline, NetworkStatus.Online),
            NetworkStatus.entries.toSet(),
        )
    }

    // F-K10 (wording half) — a row was labelled "Skipped" while its own notification was in the shade.
    @Test
    fun qk4_an_event_that_was_recorded_but_never_offered_for_delivery_is_not_called_skipped() {
        assertEquals(DeliveryStatus.NotAttempted, EventState.INITIATED.toDeliveryStatus())
        assertEquals(DeliveryStatus.NotAttempted, EventState.COLLECTING.toDeliveryStatus())
        assertEquals(R.string.status_not_attempted, deliveryChipLabelRes(DeliveryStatus.NotAttempted))
    }

    @Test
    fun qk4_the_other_delivery_states_keep_their_own_wording() {
        assertEquals(DeliveryStatus.Delivered, EventState.DELIVERED.toDeliveryStatus())
        assertEquals(DeliveryStatus.Deferred, EventState.DEFERRED.toDeliveryStatus())
        assertEquals(DeliveryStatus.Failed, EventState.FAILED_FINAL.toDeliveryStatus())
        assertEquals(R.string.status_delivered, deliveryChipLabelRes(DeliveryStatus.Delivered))
        assertEquals(R.string.status_failed, deliveryChipLabelRes(DeliveryStatus.Failed))
    }

    @Test
    fun qk4_the_state_subtitle_still_names_the_state_that_ran() {
        assertEquals(R.string.status_recorded, eventStateLabelRes(EventState.INITIATED))
        assertEquals(R.string.status_collecting, eventStateLabelRes(EventState.COLLECTING))
    }

    // The fixed-form age string read "1 hours ago". Every tier must come from a quantity resource.
    @Test
    fun qk4_every_age_tier_is_a_plural_resource_so_one_of_something_reads_as_one() {
        assertEquals(R.plurals.event_age_days, ageQuantityRes(AgeParts.Days(1)))
        assertEquals(R.plurals.event_age_hours, ageQuantityRes(AgeParts.Hours(1)))
        assertEquals(R.plurals.event_age_minutes, ageQuantityRes(AgeParts.Minutes(1)))
        assertEquals(R.plurals.event_age_seconds, ageQuantityRes(AgeParts.Seconds(1)))
    }
}
