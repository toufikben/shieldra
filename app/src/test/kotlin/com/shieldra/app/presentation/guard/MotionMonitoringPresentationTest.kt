package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.runtime.MonitoringClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The dashboard may render Motion as running only while a service and a live sensor registration
 * both say so (amendment A-18, correction (ب)). The claim is the single input, so the projection is
 * pure Kotlin and the no-overclaiming rule is testable without Android — the same shape
 * [LockGuardDecisionTest] pins for the storage states.
 */
class MotionMonitoringPresentationTest {

    @Test
    fun a_service_that_is_not_running_renders_the_motion_guard_as_disabled() {
        assertEquals(GuardStatus.Disabled, MotionMonitoringPresentation.guardStatus(MonitoringClaim.NoExecution))
    }

    @Test
    fun a_running_service_without_a_live_sensor_registration_renders_disabled_not_active() {
        assertEquals(
            GuardStatus.Disabled,
            MotionMonitoringPresentation.guardStatus(MonitoringClaim.ExecutionWithoutDetector),
            "an execution layer with nothing observing is not monitoring",
        )
    }

    @Test
    fun only_a_live_registration_renders_the_motion_guard_as_active() {
        assertEquals(
            GuardStatus.Active,
            MotionMonitoringPresentation.guardStatus(MonitoringClaim.ActiveGuardMonitoring),
        )
    }

    @Test
    fun no_claim_ever_renders_a_signal_or_a_locked_guard() {
        val rendered = MonitoringClaim.entries.map { MotionMonitoringPresentation.guardStatus(it) }.toSet()

        assertEquals(setOf(GuardStatus.Disabled, GuardStatus.Active), rendered)
    }

    @Test
    fun a_guard_that_is_not_monitoring_keeps_the_copy_that_says_so() {
        assertEquals(
            com.shieldra.app.R.string.guard_subtitle_inactive,
            MotionMonitoringPresentation.subtitleRes(MonitoringClaim.NoExecution),
        )
        assertEquals(
            com.shieldra.app.R.string.guard_subtitle_inactive,
            MotionMonitoringPresentation.subtitleRes(MonitoringClaim.ExecutionWithoutDetector),
        )
    }

    @Test
    fun the_active_copy_is_reserved_for_an_actively_monitoring_guard() {
        assertEquals(
            com.shieldra.app.R.string.guard_subtitle_motion_active,
            MotionMonitoringPresentation.subtitleRes(MonitoringClaim.ActiveGuardMonitoring),
        )
    }

    @Test
    fun the_protection_screen_states_an_active_motion_guard() {
        assertEquals(
            MotionMonitoringPresentation.SetupStatus.ACTIVE,
            MotionMonitoringPresentation.setupStatus(MonitoringClaim.ActiveGuardMonitoring, notificationsGranted = true),
        )
        assertEquals(
            com.shieldra.app.R.string.protection_motion_active,
            MotionMonitoringPresentation.setupTextRes(MotionMonitoringPresentation.SetupStatus.ACTIVE),
        )
    }

    @Test
    fun a_missing_notification_permission_is_stated_before_any_start() {
        assertEquals(
            MotionMonitoringPresentation.SetupStatus.NEEDS_NOTIFICATION_PERMISSION,
            MotionMonitoringPresentation.setupStatus(MonitoringClaim.NoExecution, notificationsGranted = false),
        )
        assertEquals(
            com.shieldra.app.R.string.protection_motion_needs_notifications,
            MotionMonitoringPresentation.setupTextRes(
                MotionMonitoringPresentation.SetupStatus.NEEDS_NOTIFICATION_PERMISSION,
            ),
        )
    }

    @Test
    fun an_allowed_but_not_running_guard_offers_a_plain_start() {
        assertEquals(
            MotionMonitoringPresentation.SetupStatus.NOT_STARTED,
            MotionMonitoringPresentation.setupStatus(MonitoringClaim.ExecutionWithoutDetector, notificationsGranted = true),
        )
        assertEquals(
            com.shieldra.app.R.string.protection_motion_not_started,
            MotionMonitoringPresentation.setupTextRes(MotionMonitoringPresentation.SetupStatus.NOT_STARTED),
        )
    }

    @Test
    fun no_setup_status_claims_monitoring_without_a_live_registration() {
        val inactive = MonitoringClaim.entries.filterNot { it.claimsProtection }
        for (claim in inactive) {
            assertNotEquals(
                MotionMonitoringPresentation.SetupStatus.ACTIVE,
                MotionMonitoringPresentation.setupStatus(claim, notificationsGranted = true),
                "$claim has no live registration and must not render ACTIVE",
            )
        }
    }
}
