package com.seagori.hgr.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 기록 한 건을 활동 달력에 넣기 위한 최소 정보. */
data class ActivityEvent(
    val timestamp: Long,
    val done: Boolean,
    val partial: Boolean = false,
    val durationSec: Int? = null
)

data class ActivityDay(
    val date: LocalDate,
    val attempts: Int,
    val completions: Int,
    val minutes: Int
) {
    val active: Boolean get() = attempts > 0
}

/**
 * 최근 N일 활동 요약. 달력 히트맵과 연속 기록을 그리는 데 필요한 값만 담는다.
 * [days]는 오래된 날짜부터 오늘 순서로 정렬된다.
 */
data class ActivitySummary(
    val days: List<ActivityDay> = emptyList(),
    val activeDays: Int = 0,
    val totalAttempts: Int = 0,
    val totalCompletions: Int = 0,
    val totalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val bestDay: Int = 0,
    val thisWeek: Int = 0,
    val lastWeek: Int = 0
) {
    val achievement: Float
        get() = if (days.isEmpty()) 0f else activeDays.toFloat() / days.size

    /** 지난주 기록이 없으면 null. */
    val weekDeltaPercent: Int?
        get() = if (lastWeek <= 0) null else ((thisWeek - lastWeek) * 100) / lastWeek
}

object ActivityCalendar {
    const val DEFAULT_DAYS = 14

    fun build(
        events: List<ActivityEvent>,
        days: Int = DEFAULT_DAYS,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): ActivitySummary {
        require(days > 0) { "days must be positive" }
        val start = today.minusDays((days - 1).toLong())
        val byDate = mutableMapOf<LocalDate, MutableList<ActivityEvent>>()
        val activeDates = mutableSetOf<LocalDate>()
        var inWindowCount = 0

        for (event in events) {
            if (event.timestamp <= 0L) continue
            val date = Instant.ofEpochMilli(event.timestamp).atZone(zone).toLocalDate()
            activeDates += date
            if (!date.isBefore(start) && !date.isAfter(today)) {
                byDate.getOrPut(date) { mutableListOf() }.add(event)
                inWindowCount++
            }
        }

        val daily = (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dayEvents = byDate[date].orEmpty()
            ActivityDay(
                date = date,
                attempts = dayEvents.size,
                completions = dayEvents.count { it.done },
                minutes = dayEvents.sumOf { it.durationSec ?: 0 } / 60
            )
        }

        // 오늘 아직 기록 전이어도 어제까지 이어진 연속 기록은 유지한다.
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var streak = 0
        while (cursor in activeDates) {
            streak++
            cursor = cursor.minusDays(1)
        }

        val weekStart = today.minusDays(6)
        val lastWeekStart = today.minusDays(13)
        val lastWeekEnd = weekStart.minusDays(1)
        var thisWeek = 0
        var lastWeek = 0
        for ((date, dayEvents) in byDate) {
            if (!date.isBefore(weekStart)) {
                thisWeek += dayEvents.size
            }
            if (!date.isBefore(lastWeekStart) && !date.isAfter(lastWeekEnd)) {
                lastWeek += dayEvents.size
            }
        }

        return ActivitySummary(
            days = daily,
            activeDays = daily.count { it.active },
            totalAttempts = daily.sumOf { it.attempts },
            totalCompletions = daily.sumOf { it.completions },
            totalMinutes = daily.sumOf { it.minutes },
            currentStreak = streak,
            bestDay = daily.maxOfOrNull { it.attempts } ?: 0,
            thisWeek = thisWeek,
            lastWeek = lastWeek
        )
    }
}
