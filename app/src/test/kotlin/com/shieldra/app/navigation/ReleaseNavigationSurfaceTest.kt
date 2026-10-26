package com.shieldra.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Q-K-1 (owner answer, 2026-10-02): the Premium tab and its route leave this
 * release. Nothing is purchasable, so no reachable screen may look purchasable.
 *
 * A-40 (2026-10-27) adds a REAL Pro subscription, so the Pro screen (route "main/pro") exists; the Q-K-1
 * rule that remains is that no route may name a Premium screen. The Map tab was added at the owner's
 * request (2026-10-22); it is a functional
 * tracking surface, not a purchasable one, so the Q-K-1 premium restriction below
 * is unchanged. These assertions hold the shape of the released surface: exactly
 * these tabs, and no declared destination that names a premium screen.
 */
class ReleaseNavigationSurfaceTest {
    @Test
    fun qk1_released_tab_bar_has_exactly_dashboard_map_history_settings() {
        assertEquals(
            setOf(
                ShieldraRoutes.DASHBOARD,
                ShieldraRoutes.MAP,
                ShieldraRoutes.HISTORY,
                ShieldraRoutes.SETTINGS,
            ),
            BottomTab.entries.map { it.route }.toSet(),
        )
    }

    @Test
    fun qk1_no_declared_route_names_a_premium_screen() {
        val routes = ShieldraRoutes::class.java.declaredFields
            .map { it.get(null) }
            .filterIsInstance<String>()
        assertTrue(routes.isNotEmpty(), "the route table declares no destinations")
        assertFalse(
            routes.any { it.contains("premium", ignoreCase = true) },
            "a premium destination is still declared: $routes",
        )
    }
}
