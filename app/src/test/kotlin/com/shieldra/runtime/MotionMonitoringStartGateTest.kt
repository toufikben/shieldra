package com.shieldra.runtime

import com.shieldra.domain.InMemoryEventRepository
import com.shieldra.domain.PanicActivation
import com.shieldra.domain.PanicIncidentCoordinator
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Batch G / slice G5, Q-G5-b (amendment A-18): Motion monitoring is refused when its ongoing
 * notification would not be visible.
 *
 * The reason is a product promise, not a platform claim. Android keeps a foreground service running
 * after the user refuses `POST_NOTIFICATIONS`; what it does not do is show the notification in the
 * drawer. A-15 conditions Motion monitoring on the user being able to see it, so SHIELDRA declines
 * to start rather than start and misdescribe itself. Refusal never reaches the Panic path, which
 * records whether or not a notification can be posted (A-6).
 */
class MotionMonitoringStartGateTest {
    private val apiWithRuntimeNotificationPermission = 33
    private val apiWithoutRuntimeNotificationPermission = 31
    private val apiOnTheShippingDevice = 36

    @Test
    fun monitoring_may_start_on_a_version_with_no_notification_permission_to_ask_for() {
        val decision = MotionMonitoringStartGate.decide(
            sdkInt = apiWithoutRuntimeNotificationPermission,
            notificationGranted = null,
        )
        assertIs<MotionMonitoringStartDecision.MayStartMonitoring>(decision)
    }

    @Test
    fun monitoring_may_start_when_the_live_notification_permission_is_granted() {
        assertIs<MotionMonitoringStartDecision.MayStartMonitoring>(
            MotionMonitoringStartGate.decide(apiWithRuntimeNotificationPermission, true),
            "API 33 granted is the ordinary case",
        )
        assertIs<MotionMonitoringStartDecision.MayStartMonitoring>(
            MotionMonitoringStartGate.decide(apiOnTheShippingDevice, true),
            "the shipping device is Android 16 with the permission granted",
        )
    }

    @Test
    fun a_refused_notification_permission_does_not_start_monitoring() {
        val decision = MotionMonitoringStartGate.decide(apiOnTheShippingDevice, false)

        val refused = assertIs<MotionMonitoringStartDecision.DoNotStartMonitoring>(decision)
        assertTrue(refused.reason.isNotBlank(), "a refusal the UI cannot show is not a refusal")
    }

    @Test
    fun an_unanswered_notification_permission_routes_to_the_request_instead_of_starting() {
        val decision = MotionMonitoringStartGate.decide(apiWithRuntimeNotificationPermission, null)

        assertIs<MotionMonitoringStartDecision.AskForNotificationPermission>(
            decision,
            "not-yet-asked is neither a grant nor a refusal: the user is directed to notification setup",
        )
    }

    @Test
    fun the_refusal_reason_is_the_product_promise_and_never_blames_android_for_the_service() {
        val refused = assertIs<MotionMonitoringStartDecision.DoNotStartMonitoring>(
            MotionMonitoringStartGate.decide(apiOnTheShippingDevice, false),
        )
        val reason = refused.reason.lowercase()

        assertTrue(reason.contains("notification"), "the reason must name the notification: $reason")
        for (blame in PLATFORM_BLAME) {
            assertTrue(!reason.contains(blame), "the reason may not claim Android blocks the service itself ($blame): $reason")
        }
    }

    @Test
    fun a_monitoring_refusal_never_blocks_a_panic_activation() {
        val repository = InMemoryEventRepository()
        val coordinator = PanicIncidentCoordinator(repository = repository)

        val decision = MotionMonitoringStartGate.decide(apiOnTheShippingDevice, false)
        assertIs<MotionMonitoringStartDecision.DoNotStartMonitoring>(decision)

        val activation = assertIs<PanicActivation.Recorded>(coordinator.activate())
        assertTrue(
            repository.listRecent(50).any { it.id == activation.event.id },
            "Q-G5-b is a Motion monitoring gate, not a permission gate for recording events",
        )
    }

    private companion object {
        /** Phrases that would turn a product decision into a false claim about the platform. */
        val PLATFORM_BLAME = listOf(
            "android does not allow",
            "android doesn't allow",
            "android forbids",
            "android blocks",
            "android prevents",
            "android refuses",
            "not allowed by android",
            "android requires",
        )
    }
}
