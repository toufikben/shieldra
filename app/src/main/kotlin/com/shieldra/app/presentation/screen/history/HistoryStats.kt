package com.shieldra.app.presentation.screen.history

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure arithmetic behind the History screen's week chart and day groups. Every number is counted from
 * the persisted events' own timestamps; nothing is estimated. Kept free of Android so it is
 * unit-tested with fixed zones and instants.
 */
object HistoryStats {

    enum class DayBucket { TODAY, YESTERDAY, EARLIER }

    /** One chart column: the calendar day, its weekday, and how many events fell on it. */
    data class DayCount(val date: LocalDate, val weekday: DayOfWeek, val count: Int, val isToday: Boolean)

    /** Seven columns, oldest first, ending with today, counting events by local calendar day. */
    fun weekly(eventTimesMs: List<Long>, nowMs: Long, zone: ZoneId): List<DayCount> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val perDay = eventTimesMs
            .filter { it > 0L }
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()
        return (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            DayCount(date = day, weekday = day.dayOfWeek, count = perDay[day] ?: 0, isToday = back == 0)
        }
    }

    fun bucket(eventMs: Long, nowMs: Long, zone: ZoneId): DayBucket {
        if (eventMs <= 0L) return DayBucket.EARLIER
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(eventMs).atZone(zone).toLocalDate()
        return when {
            day == today -> DayBucket.TODAY
            day == today.minusDays(1) -> DayBucket.YESTERDAY
            else -> DayBucket.EARLIER
        }
    }

    /** The calendar day an event belongs to, for grouping the "earlier" events under dated headers. */
    fun dayOf(eventMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(eventMs).atZone(zone).toLocalDate()
}
