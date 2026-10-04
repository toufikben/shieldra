package com.shieldra.app.presentation.settings

import com.shieldra.runtime.MonitoringClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Batch F honesty fixes (owner directive F1/F7.8–F7.10), kept in pure Kotlin
 * so the decision — which row exists and what it is allowed to say — is testable
 * without Android. The exact wording is resources; the static gate proves the
 * fabricated wording is gone, and the device proves what the screen shows.
 */
class SettingsContentTest {

    // F7.9 — no Settings row may imply a delivery channel that does not exist.
    @Test
    fun the_settings_rows_have_a_notification_entry_and_no_delivery_entry() {
        val ids = SettingsContent.rowIds()

        assertTrue(SettingsContent.NOTIFICATION_ROW_ID in ids)
        assertFalse("delivery" in ids)
    }

    @Test
    fun the_notification_row_is_the_one_that_opens_the_real_permission_screen() {
        assertEquals(
            SettingsRowTarget.NotificationSetup,
            SettingsContent.targetOf(SettingsContent.NOTIFICATION_ROW_ID),
        )
    }

    @Test
    fun a_row_that_does_nothing_yet_is_declared_dead_instead_of_promising_a_screen() {
        assertEquals(SettingsRowTarget.NotAvailable, SettingsContent.targetOf("retention"))
        assertEquals(SettingsRowTarget.NotAvailable, SettingsContent.targetOf("unknown_row"))
    }

    // F7.10 — the storage row reports what storage really is, never a demo number.
    @Test
    fun the_storage_row_reflects_whether_storage_was_actually_installed() {
        assertEquals(
            SettingsContent.StorageRowState.NotInstalled,
            SettingsContent.storageRowState(storageInstalled = false),
        )
        assertEquals(
            SettingsContent.StorageRowState.InUse,
            SettingsContent.storageRowState(storageInstalled = true),
        )
    }

    @Test
    fun the_storage_row_has_no_fabricated_event_or_byte_count() {
        // An event count cannot be read through the existing storage API, so the
        // row must not print figures at all.
        assertFalse(SettingsContent.storageRowShowsFigures)
    }

    // F7.8 — the dashboard mode follows the real storage state.
    @Test
    fun the_dashboard_disclosure_stops_calling_real_storage_a_presentation_demo() {
        assertEquals(
            SettingsContent.DashboardMode.LocalEventsOnly,
            SettingsContent.dashboardMode(storageInstalled = true, motionMonitoring = false),
        )
        assertEquals(
            SettingsContent.DashboardMode.NoStorageYet,
            SettingsContent.dashboardMode(storageInstalled = false, motionMonitoring = false),
        )
    }

    // G5 truthfulness fix D: the disclosure line follows the same Motion truth the tile follows, so
    // it cannot tell the owner monitoring is absent while the monitor is reporting samples.
    @Test
    fun the_dashboard_disclosure_stops_denying_monitoring_while_motion_is_monitoring() {
        assertEquals(
            SettingsContent.DashboardMode.LocalEventsWithMotion,
            SettingsContent.dashboardMode(storageInstalled = true, motionMonitoring = true),
        )
        assertTrue(
            SettingsContent.dashboardMode(storageInstalled = true, motionMonitoring = true).claimsMonitoring,
            "The monitoring mode is the one state the running service really proves",
        )
        assertNotEquals(
            disclosureLabelRes(SettingsContent.DashboardMode.LocalEventsOnly),
            disclosureLabelRes(SettingsContent.DashboardMode.LocalEventsWithMotion),
            "An active monitor must not be described by the sentence that denies monitoring",
        )
    }

    @Test
    fun only_the_monitoring_mode_claims_monitoring() {
        SettingsContent.DashboardMode.entries.forEach { mode ->
            assertEquals(
                mode == SettingsContent.DashboardMode.LocalEventsWithMotion,
                mode.claimsMonitoring,
                "$mode claims monitoring, and only the running Motion monitor may",
            )
        }
        assertFalse(MonitoringClaim.ExecutionWithoutDetector.claimsProtection)
    }

    // Q-K-9 (owner directive 2026-10-02): the first row was titled "Protection" and subtitled
    // "Lock Guard · Guards" above a target that opened nothing, so the Settings screen advertised a
    // guard the owner could supposedly turn on. The whole row list is pinned, which means neither
    // that row nor a renamed replacement can come back without this test being edited first.
    @Test
    fun qk9_no_settings_row_promises_a_lock_guard_the_owner_cannot_turn_on() {
        assertFalse(
            "protection" in SettingsContent.rowIds(),
            "A Settings row named after a guard the build does not run is a claim, not a setting",
        )
        assertEquals(
            listOf(
                "notifications",
                "alert_channels",
                "app_lock",
                "theme",
                "language",
                "storage",
                "retention",
                "about",
                "help",
            ),
            SettingsContent.rowIds(),
        )
    }
}
