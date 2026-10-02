package com.shieldra.app.navigation

object ShieldraRoutes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PERMISSIONS = "onboarding/permissions"
    const val ONBOARDING_PROTECTION = "onboarding/protection"

    const val DASHBOARD = "main/dashboard"
    const val HISTORY = "main/history"
    const val SETTINGS = "main/settings"
    /** Notification setup (amendment A-6): the only place the permission is requested. */
    const val NOTIFICATION_SETUP = "main/settings/notifications"

    const val EVENT_DETAIL_ARG_ID = "eventId"
    const val EVENT_DETAIL = "main/event/{$EVENT_DETAIL_ARG_ID}"

    fun eventDetail(eventId: String): String = "main/event/$eventId"
}

enum class BottomTab(val route: String) {
    Dashboard(ShieldraRoutes.DASHBOARD),
    History(ShieldraRoutes.HISTORY),
    Settings(ShieldraRoutes.SETTINGS),
}
