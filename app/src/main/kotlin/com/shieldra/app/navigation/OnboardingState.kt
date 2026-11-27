package com.shieldra.app.navigation

import android.content.Context

/**
 * Whether this app has already been through onboarding (Q-K-7, amendment A-27).
 *
 * Onboarding is a one-time flow, so its completion has to outlive the process. On
 * the owner's RMX3910 the system reclaims the app process constantly, and a start
 * destination picked from a constant treated every cold start as a new user — a
 * phone holding real encrypted evidence was shown the welcome page again.
 *
 * This is settings-level flow state, exactly like the notification permission
 * record: it is not written into any Security Event, and no event's truth depends
 * on it.
 */
interface OnboardingCompletionRecord {
    fun isCompleted(): Boolean

    fun markCompleted()
}

/**
 * The preference-backed [OnboardingCompletionRecord].
 *
 * One boolean in app-private storage, deliberately not in the Room database: the
 * database holds security events and their truth, and this is neither. Keeping it
 * outside the database is also what makes a reinstall or an app-data reset return
 * the app to a normal first run — the storage layer's own recovery behaviour is
 * untouched by this flag.
 */
class PreferencesOnboardingCompletionRecord(context: Context) : OnboardingCompletionRecord {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override fun markCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME: String = "shieldra_onboarding"
        const val KEY_ONBOARDING_COMPLETED: String = "onboarding_completed"
    }
}

/**
 * The one decision onboarding persistence has to make, kept out of Compose so it
 * is testable: a recorded completion starts the app on the dashboard, and nothing
 * else reopens the flow.
 */
object OnboardingState {
    /**
     * P4-22: the start destination depends on two recorded facts, in this order.
     *
     * Setup comes first, always: a phone that has never been configured opens the welcome page, because
     * the tour describes a product the owner has not turned on yet and would explain switches he cannot
     * see. Once setup is done, a tour he has not seen opens once. After that, the dashboard.
     *
     * There is deliberately no one-argument overload: a caller that forgot the tour would silently skip
     * it on every install, and that is not a mistake worth making easy.
     */
    fun startDestination(onboardingCompleted: Boolean, introCardsSeen: Boolean): String = when {
        !onboardingCompleted -> ShieldraRoutes.ONBOARDING_WELCOME
        else -> ShieldraRoutes.DASHBOARD
    }
}
