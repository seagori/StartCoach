package com.seagori.hgr.desktop

import com.seagori.hgr.domain.ActivityCalendar
import com.seagori.hgr.domain.ActivityEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityCalendarTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 11)

    private fun at(daysAgo: Long, hour: Int = 12): Long =
        today.minusDays(daysAgo).atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun build(vararg events: ActivityEvent) =
        ActivityCalendar.build(events.toList(), zone = zone, today = today)

    @Test
    fun fillsFourteenDaysEndingToday() {
        val summary = build(ActivityEvent(at(0), done = true))
        assertEquals(14, summary.days.size)
        assertEquals(today.minusDays(13), summary.days.first().date)
        assertEquals(today, summary.days.last().date)
        assertEquals(1, summary.activeDays)
        assertEquals(1, summary.totalAttempts)
        assertTrue(summary.days.last().active)
    }

    @Test
    fun streakCountsBackFromToday() {
        val summary = build(
            ActivityEvent(at(0), done = true),
            ActivityEvent(at(1), done = false),
            ActivityEvent(at(2), done = true)
        )
        assertEquals(3, summary.currentStreak)
        assertEquals(3, summary.activeDays)
    }

    @Test
    fun streakSurvivesADayWithoutRecordsYet() {
        val summary = build(
            ActivityEvent(at(1), done = true),
            ActivityEvent(at(2), done = true)
        )
        assertEquals(2, summary.currentStreak)
    }

    @Test
    fun streakStopsAtTheFirstGap() {
        val summary = build(
            ActivityEvent(at(1), done = true),
            ActivityEvent(at(3), done = true)
        )
        assertEquals(1, summary.currentStreak)
    }

    @Test
    fun splitsThisWeekAndLastWeek() {
        val summary = build(
            ActivityEvent(at(0), done = true),
            ActivityEvent(at(6), done = true),
            ActivityEvent(at(7), done = true),
            ActivityEvent(at(13), done = true)
        )
        assertEquals(2, summary.thisWeek)
        assertEquals(2, summary.lastWeek)
        assertEquals(0, summary.weekDeltaPercent)
        assertEquals(4, summary.totalAttempts)
    }

    @Test
    fun ignoresRecordsOutsideTheWindow() {
        val summary = build(
            ActivityEvent(at(0), done = true),
            ActivityEvent(at(20), done = true)
        )
        assertEquals(1, summary.totalAttempts)
        assertEquals(1, summary.activeDays)
    }

    @Test
    fun aggregatesCompletionsAndMinutesPerDay() {
        val summary = build(
            ActivityEvent(at(0, 9), done = true, durationSec = 600),
            ActivityEvent(at(0, 10), done = false, durationSec = 300),
            ActivityEvent(at(0, 11), done = true, partial = true)
        )
        val todayActivity = summary.days.last()
        assertEquals(3, todayActivity.attempts)
        assertEquals(2, todayActivity.completions)
        assertEquals(15, todayActivity.minutes)
        assertEquals(15, summary.totalMinutes)
        assertEquals(3, summary.bestDay)
    }

    @Test
    fun noRecordsMeansZeroAchievement() {
        val summary = build()
        assertEquals(0f, summary.achievement)
        assertEquals(0, summary.currentStreak)
        assertEquals(null, summary.weekDeltaPercent)
    }
}
