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
