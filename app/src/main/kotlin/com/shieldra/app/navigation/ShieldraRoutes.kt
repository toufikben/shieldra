package com.shieldra.app.navigation

object ShieldraRoutes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PERMISSIONS = "onboarding/permissions"
    /** The notification setup screen as a first-run step: same screen, same single request (A-6). */
    const val ONBOARDING_NOTIFICATIONS = "onboarding/notifications"
    const val ONBOARDING_PROTECTION = "onboarding/protection"

    const val DASHBOARD = "main/dashboard"
    const val MAP = "main/map"
    const val HISTORY = "main/history"
    const val SETTINGS = "main/settings"
    /** Notification setup (amendment A-6): the only place the permission is requested. */
    const val NOTIFICATION_SETUP = "main/settings/notifications"
    /** Alert channels (amendment A-32): the owner's Telegram bot and email account. */
    const val ALERT_CHANNELS = "main/settings/alerts"
    /** Protection setup (amendments A-33/A-34): arm the guards and tune them. */
    const val PROTECTION_SETUP = "main/settings/protection"
    /** Read-only information pages behind the Settings rows: real figures, the retention policy, about, help. */
    const val STORAGE_INFO = "main/settings/storage"
    const val RETENTION_INFO = "main/settings/retention"
    const val ABOUT_INFO = "main/settings/about"
    const val HELP_INFO = "main/settings/help"
    const val PRIVACY_INFO = "main/settings/privacy"
    /**
     * The inverted help screen: the steps to take in the minute the owner knows the phone is gone.
     * Deliberately reachable from Settings without arming anything — the owner may need it on the
     * day they never set the app up properly, and a guide behind a gate is a guide nobody reads.
     */
    const val PANIC_GUIDE = "main/settings/panic-guide"
    /**
     * The vendor battery-manager guide (audit development ب). Reached from the protection screen and
     * from onboarding, never as a Settings row of its own: it is a prerequisite of the guards working,
     * not a setting, and the two places an owner meets it are the two places they decide to arm.
     */
    const val SURVIVAL_GUIDE = "main/settings/battery-survival"
    /** The comparison with Android's own theft protection (audit development د): what the OS already does. */
    const val PLATFORM_COMPARISON = "main/settings/platform-comparison"
    /** The Pro subscription screen (A-40): plans and prices come from Google Play. */
    const val PRO = "main/pro"
    /** Read-only protection health check: what is really protecting the phone now. */
    const val HEALTH_CHECK = "main/health"

    const val EVENT_DETAIL_ARG_ID = "eventId"
    const val EVENT_DETAIL = "main/event/{$EVENT_DETAIL_ARG_ID}"

    fun eventDetail(eventId: String): String = "main/event/$eventId"
}

enum class BottomTab(val route: String) {
    Dashboard(ShieldraRoutes.DASHBOARD),
    Map(ShieldraRoutes.MAP),
    History(ShieldraRoutes.HISTORY),
    Settings(ShieldraRoutes.SETTINGS),
}
