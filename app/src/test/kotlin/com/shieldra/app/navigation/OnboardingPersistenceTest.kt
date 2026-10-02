package com.shieldra.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Q-K-7 (amendment A-27): onboarding is a one-time flow, so finishing it has to survive the process
 * dying. On the owner's RMX3910 process death is the normal path rather than an edge case, and a
 * hardcoded welcome start made every cold start treat a phone holding real evidence as a new user.
 *
 * The decision lives here, outside Compose, so the mapping is testable: one recorded boolean in
 * app-private storage selects the start destination, and nothing else gets to reopen onboarding.
 */
class OnboardingPersistenceTest {

    @Test
    fun qk7_a_first_run_starts_at_the_welcome_screen() {
        assertEquals(
            ShieldraRoutes.ONBOARDING_WELCOME,
            OnboardingState.startDestination(onboardingCompleted = false),
        )
    }

    @Test
    fun qk7_a_cold_start_after_onboarding_starts_at_the_dashboard() {
        assertEquals(
            ShieldraRoutes.DASHBOARD,
            OnboardingState.startDestination(onboardingCompleted = true),
        )
    }

    @Test
    fun qk7_a_completed_onboarding_never_starts_inside_the_onboarding_flow() {
        val onboardingRoutes = setOf(
            ShieldraRoutes.ONBOARDING_WELCOME,
            ShieldraRoutes.ONBOARDING_PERMISSIONS,
            ShieldraRoutes.ONBOARDING_PROTECTION,
        )
        val start = OnboardingState.startDestination(onboardingCompleted = true)

        assertNotEquals(ShieldraRoutes.ONBOARDING_WELCOME, start, "The welcome page must not come back")
        assertFalse(
            start in onboardingRoutes,
            "A finished onboarding may not restart any part of itself: $start",
        )
    }

    @Test
    fun qk7_the_recorded_flag_drives_the_start_destination() {
        var marked = false
        val record = object : OnboardingCompletionRecord {
            override fun isCompleted(): Boolean = marked
            override fun markCompleted() { marked = true }
        }

        assertEquals(
            ShieldraRoutes.ONBOARDING_WELCOME,
            OnboardingState.startDestination(record.isCompleted()),
            "Nothing recorded yet means a first run",
        )
        record.markCompleted()
        assertEquals(
            ShieldraRoutes.DASHBOARD,
            OnboardingState.startDestination(record.isCompleted()),
            "One recorded completion is enough for every later start",
        )
    }
}
