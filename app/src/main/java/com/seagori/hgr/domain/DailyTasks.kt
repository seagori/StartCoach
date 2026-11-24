package com.seagori.hgr.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.roundToInt

@Serializable
data class DailyTaskItem(
    val id: Long,
    val title: String,
    val sortOrder: Int = 0
)

@Serializable
data class DailyTaskState(
    val tasks: List<DailyTaskItem> = emptyList(),
    val completedTodayDate: String = "",
    val completedTaskIds: Set<Long> = emptySet(),
    val dailyRates: Map<String, Int> = emptyMap()
) {
    fun effectiveCompletedIds(today: String = todayDateKey()): Set<Long> =
        if (completedTodayDate == today) completedTaskIds else emptySet()

    fun todayPercent(today: String = todayDateKey()): Int {
        if (tasks.isEmpty()) return 0
        val validIds = tasks.map { it.id }.toSet()
        val done = effectiveCompletedIds(today).count { it in validIds }
        return ((done * 100f) / tasks.size).roundToInt().coerceIn(0, 100)
    }

    fun addTask(rawTitle: String, today: String = todayDateKey()): DailyTaskState {
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty()) return this
        val nextId = (tasks.maxOfOrNull { it.id } ?: 0L) + 1L
        val nextOrder = (tasks.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val nextTasks = tasks + DailyTaskItem(id = nextId, title = trimmed, sortOrder = nextOrder)
        return copy(tasks = nextTasks).syncTodayRate(today)
    }

    fun deleteTask(taskId: Long, today: String = todayDateKey()): DailyTaskState {
        val nextTasks = tasks.filterNot { it.id == taskId }
        val nextCompleted = effectiveCompletedIds(today) - taskId
        return copy(
            tasks = nextTasks,
            completedTodayDate = today,
            completedTaskIds = nextCompleted
        ).syncTodayRate(today)
    }

    fun toggleTask(taskId: Long, today: String = todayDateKey()): DailyTaskState {
        val current = effectiveCompletedIds(today)
        val next = if (taskId in current) current - taskId else current + taskId
        return copy(
            completedTodayDate = today,
            completedTaskIds = next
        ).syncTodayRate(today)
    }

    private fun syncTodayRate(today: String): DailyTaskState {
        val updatedRates = dailyRates.toMutableMap()
        if (tasks.isEmpty()) {
            updatedRates.remove(today)
        } else {
            updatedRates[today] = todayPercent(today)
        }
        val cutoff = LocalDate.now().minusDays(400).toString()
        val pruned = updatedRates.filterKeys { it >= cutoff }
        return copy(
            completedTodayDate = today,
            completedTaskIds = effectiveCompletedIds(today),
            dailyRates = pruned
        )
    }

    companion object {
        fun todayDateKey(): String = LocalDate.now().toString()
    }
}
