package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dashboard still declares a Recent list it was deliberately told to stop drawing.
 *
 * `7184c2e` removed the Recent events list from Home on purpose: "History owns it". The screen went,
 * but the shape it consumed did not. `DashboardUiModel` still carries `recentEvents`, the factory still
 * takes it and copies it through, the host still computes a page and slices it to `RECENT_EVENT_LIMIT`,
 * and `DashboardCallbacks` still declares `onEventClick` and `onViewAllEvents` — both wired by the host
 * to real navigation, neither called by any Composable. `DashboardScreen` imports `EventCard` and
 * `EmptyState` and draws neither.
 *
 * Dead wiring in a UI contract is not cosmetic. It is a standing invitation to the next change: a
 * caller that believes the dashboard shows events will keep feeding it a page it queries and maps on
 * every write, and a reader of `onViewAllEvents` will believe there is a "View all" control to press.
 * The two strings that used to label that list (`dashboard_section_recent`, `dashboard_view_all`) were
 * left in both languages until P4-15 removed them, which is how long the half-removed list sat here.
 *
 * The test pins the removal end to end: the model no longer carries the field, the factory no longer
 * accepts or forwards it, the host no longer builds or slices a page for it, and the callbacks no
 * longer offer a click or a "view all" that nothing raises.
 */
class DashboardRecentListRemovalTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    /** The premise: the screen draws no list, so every carrier of one is dead weight. */
    @Test
    fun p4_16_the_screen_draws_no_event_card() {
        val screen = main("kotlin/com/shieldra/app/presentation/screen/dashboard/DashboardScreen.kt")
        assertFalse(
            screen.contains("EventCard("),
            "the dashboard renders an EventCard, so the list was not actually removed and this " +
                "finding must be re-measured",
        )
        assertTrue(
            screen.contains("RECENT_EVENT_LIMIT").not(),
            "the screen still knows a recent-list cap",
        )
    }

    /** The model keeps no field the screen cannot show. */
    @Test
    fun p4_16_the_model_carries_no_recent_list() {
        val models = main("kotlin/com/shieldra/app/presentation/model/PresentationModels.kt")
        assertFalse(
            models.contains("val recentEvents: List<EventUiModel>"),
            "DashboardUiModel still declares recentEvents, though nothing draws it",
        )
    }

    /** The factory neither accepts nor forwards it. */
    @Test
    fun p4_16_the_factory_neither_takes_nor_forwards_it() {
        val factory = main("kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt")
        assertFalse(
            factory.contains("recentEvents: List<EventUiModel>"),
            "lockGuardDashboard still takes a page of events it cannot display",
        )
        assertFalse(
            factory.contains("recentEvents = recentEvents"),
            "lockGuardDashboard still forwards a page of events into the model",
        )
    }

    /** The host stops building and slicing a page the dashboard ignores. */
    @Test
    fun p4_16_the_host_stops_slicing_a_page_for_it() {
        val host = main("kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertFalse(
            host.contains("recentEvents.take(RECENT_EVENT_LIMIT)"),
            "the host still slices the History page down for a dashboard list that does not exist",
        )
        assertFalse(
            host.contains("RECENT_EVENT_LIMIT"),
            "RECENT_EVENT_LIMIT still exists, so a second list could be reintroduced by accident",
        )
    }

    /** And the callbacks offer nothing the screen never raises. */
    @Test
    fun p4_16_the_callbacks_offer_no_dead_actions() {
        val callbacks = main("kotlin/com/shieldra/app/presentation/screen/dashboard/DashboardCallbacks.kt")
        assertFalse(
            callbacks.contains("val onEventClick"),
            "DashboardCallbacks still declares onEventClick, which no Composable invokes",
        )
        assertFalse(
            callbacks.contains("val onViewAllEvents"),
            "DashboardCallbacks still declares onViewAllEvents, the 'View all' control that was removed",
        )
    }
}
