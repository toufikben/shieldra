package com.shieldra.app.presentation.screen.history

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStatsTest {

    private val zone = ZoneId.of("UTC")

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val now = ms(2026, 10, 26, 18)

    @Test
    fun `week has seven columns ending with today`() {
        val week = HistoryStats.weekly(emptyList(), now, zone)
        assertEquals(7, week.size)
        assertTrue(week.last().isToday)
        assertEquals(26, week.last().date.dayOfMonth)
        assertEquals(20, week.first().date.dayOfMonth)
    }

    @Test
    fun `events are counted on their own local day`() {
        val times = listOf(ms(2026, 10, 26, 1), ms(2026, 10, 26, 23), ms(2026, 10, 25), ms(2026, 10, 20))
        val week = HistoryStats.weekly(times, now, zone)
        assertEquals(2, week.last().count)
        assertEquals(1, week[5].count)
        assertEquals(1, week.first().count)
    }

    @Test
    fun `events older than the week are not in the chart`() {
        val week = HistoryStats.weekly(listOf(ms(2026, 10, 1)), now, zone)
        assertEquals(0, week.sumOf { it.count })
    }

    @Test
    fun `unknown timestamps are ignored by the chart`() {
        val week = HistoryStats.weekly(listOf(0L, -5L), now, zone)
        assertEquals(0, week.sumOf { it.count })
    }

    @Test
    fun `buckets split today, yesterday and earlier`() {
        assertEquals(HistoryStats.DayBucket.TODAY, HistoryStats.bucket(ms(2026, 10, 26, 0), now, zone))
        assertEquals(HistoryStats.DayBucket.YESTERDAY, HistoryStats.bucket(ms(2026, 10, 25, 23), now, zone))
        assertEquals(HistoryStats.DayBucket.EARLIER, HistoryStats.bucket(ms(2026, 10, 24), now, zone))
    }

    @Test
    fun `an event with no timestamp is earlier, never today`() {
        assertEquals(HistoryStats.DayBucket.EARLIER, HistoryStats.bucket(0L, now, zone))
    }

    @Test
    fun `the day boundary follows the zone, not UTC`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 2026-10-26 20:00 UTC is already 2026-10-27 05:00 in Tokyo.
        val late = ms(2026, 10, 26, 20)
        assertEquals(HistoryStats.DayBucket.TODAY, HistoryStats.bucket(late, late, tokyo))
        assertEquals(27, HistoryStats.dayOf(late, tokyo).dayOfMonth)
    }
}
