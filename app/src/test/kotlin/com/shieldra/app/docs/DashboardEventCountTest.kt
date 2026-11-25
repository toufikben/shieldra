package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Events tile counts a page and calls it the total.
 *
 * The tile reads `stat_events` — "Events" — and `fc373c9` was written to make it show "the real total,
 * not the newest 20". It passes `totalEvents = recentEvents.size`, and `recentEvents` is the
 * History page: `eventReader.readRecent(HISTORY_EVENT_LIMIT, ...)`, capped at 200. So on a phone that
 * has recorded more than two hundred events the Home screen reports 200 — a number that is neither the
 * total nor the newest few, and only ever travels downward as events are deleted.
 *
 * The store already answers the question honestly: `SecurityEventDao.count()` is a `SELECT COUNT(*)`
 * and `LocalShieldraStorage.figures()` publishes it as `eventRows`. Nothing on the dashboard path asks
 * it. The screen's own fallback is the second half of the same lie: when no total arrives it prints
 * `model.recentEvents.size`, which is the length of a list the dashboard no longer draws.
 *
 * The test pins the whole chain: a count the storage can answer, a reader that asks for it, the tile
 * fed by that count, and no path anywhere that lets a page length stand in for the total.
 */
class DashboardEventCountTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    /** The premise: the number the tile shows today is a page length, not a count. */
    @Test
    fun p4_14_the_page_length_is_what_the_tile_gets_today() {
        val host = main("kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertTrue(
            host.contains("eventReader.readRecent(HISTORY_EVENT_LIMIT"),
            "the dashboard no longer reads a capped page, so this finding must be re-measured",
        )
        assertTrue(
            host.contains("HISTORY_EVENT_LIMIT = 200"),
            "the page is no longer capped at 200, so a page length and a total could not disagree",
        )
    }

    /** The count the storage can already answer. */
    @Test
    fun p4_14_the_storage_publishes_a_row_count() {
        val storage = main("kotlin/com/shieldra/storage/LocalStorageFactory.kt")
        assertTrue(
            storage.contains("fun eventRowCount()"),
            "the open storage publishes no direct row count, so the dashboard has nothing honest to read " +
                "and a page length is the only number within reach",
        )
        assertTrue(
            storage.contains("securityEventDao().count()"),
            "eventRowCount() must ask the DAO's SELECT COUNT(*), not measure a list",
        )
    }

    /** The reader must be the one gateway to it, the way it is for every other persisted fact. */
    @Test
    fun p4_14_the_reader_answers_the_count() {
        val reader = main("kotlin/com/shieldra/app/presentation/guard/PersistedEventReader.kt")
        assertTrue(
            reader.contains("fun countEvents()"),
            "PersistedEventReader exposes no count, so the navigation layer would have to open storage " +
                "itself and bypass the reader that enforces the installed-storage gate",
        )
        val binding = main("kotlin/com/shieldra/app/presentation/guard/StorageEventReader.kt")
        assertTrue(
            binding.contains("loadCount"),
            "the reader is never bound to a real count loader",
        )
    }

    /** The tile reads the count, and nothing turns a page length back into a total. */
    @Test
    fun p4_14_the_tile_is_fed_the_count() {
        val host = main("kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertFalse(
            host.contains("totalEvents = recentEvents.size"),
            "the Events tile is still handed the History page's length, so on a phone holding more than " +
                "HISTORY_EVENT_LIMIT rows it claims that many events exist",
        )
        assertTrue(
            host.contains("totalEvents = eventCount"),
            "nothing feeds the tile a real count",
        )
    }

    /** And the screen may not invent one when the count has not arrived. */
    @Test
    fun p4_14_the_screen_shows_no_substitute() {
        val screen = main("kotlin/com/shieldra/app/presentation/screen/dashboard/DashboardScreen.kt")
        assertFalse(
            screen.contains("model.recentEvents.size"),
            "the Events tile still falls back to the length of the recent list, so an unread count is " +
                "printed as a number instead of being shown as unknown",
        )
    }
}
