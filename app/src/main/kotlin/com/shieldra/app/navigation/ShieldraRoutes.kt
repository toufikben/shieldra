package com.shieldra.app.navigation

object ShieldraRoutes {
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PERMISSIONS = "onboarding/permissions"
    const val ONBOARDING_PROTECTION = "onboarding/protection"

    const val DASHBOARD = "main/dashboard"
    const val HISTORY = "main/history"
    const val PREMIUM = "main/premium"
    const val SETTINGS = "main/settings"

    const val EVENT_DETAIL_ARG_ID = "eventId"
    const val EVENT_DETAIL = "main/event/{$EVENT_DETAIL_ARG_ID}"

    fun eventDetail(eventId: String): String = "main/event/$eventId"
}

enum class BottomTab(val route: String) {
    Dashboard(ShieldraRoutes.DASHBOARD),
    History(ShieldraRoutes.HISTORY),
    Premium(ShieldraRoutes.PREMIUM),
    Settings(ShieldraRoutes.SETTINGS),
}
