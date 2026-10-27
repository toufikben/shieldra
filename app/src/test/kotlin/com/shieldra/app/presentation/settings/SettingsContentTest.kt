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

    // F7.9 — no Settings row may imply a delivery channel that does not exist. Notifications are
    // folded into the alerts row (one place for where alerts leave and whether the phone may show its
    // own), so the alerts row stands in for the old standalone notification entry.
    @Test
    fun the_settings_rows_have_a_notification_entry_and_no_delivery_entry() {
        val ids = SettingsContent.rowIds()

        assertTrue(SettingsContent.ALERT_CHANNELS_ROW_ID in ids)
        assertFalse(SettingsContent.NOTIFICATION_ROW_ID in ids, "notifications are merged into the alerts row")
        assertFalse("delivery" in ids)
    }

    @Test
    fun the_alerts_row_opens_the_screen_that_holds_channels_and_the_notification_permission() {
        assertEquals(
            SettingsRowTarget.AlertChannels,
            SettingsContent.targetOf(SettingsContent.ALERT_CHANNELS_ROW_ID),
        )
    }

    @Test
    fun every_row_has_a_real_destination_and_only_an_unknown_id_is_dead() {
        SettingsContent.rowIds().forEach { id ->
            assertNotEquals(
                SettingsRowTarget.NotAvailable,
                SettingsContent.targetOf(id),
                "the row '$id' must open something real, not a deferred stub",
            )
        }
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

    // Q-K-9 (owner directive 2026-10-02) removed a "Protection" row because it opened nothing — a
    // guard the owner could not turn on was a claim, not a setting. A-33/A-34 (owner, 2026-10-13)
    // reversed the premise: real guards now run behind a real setup screen, so the row is a setting
    // again. The invariant Q-K-9 really protects is kept and strengthened here: the Protection row
    // must open an actual destination, never SettingsRowTarget.NotAvailable. The whole list stays
    // pinned so no row can reappear or move without this test being edited first.
    @Test
    fun the_protection_row_opens_a_real_setup_and_the_row_list_stays_pinned() {
        assertTrue(
            "protection" in SettingsContent.rowIds(),
            "A-33/A-34: the protection row arms real guards and must be present",
        )
        assertEquals(
            SettingsRowTarget.ProtectionSetup,
            SettingsContent.targetOf("protection"),
            "the protection row must open the setup screen, never a dead NotAvailable target",
        )
        assertEquals(
            listOf(
                "protection",
                "alert_channels",
                "health",
                "safe_zones",
                "storage",
                "retention",
                "erase_data",
                "app_lock",
                "theme",
                "language",
                "about",
                "help",
                "privacy",
            ),
            SettingsContent.rowIds(),
        )
    }
}
