package com.seagori.hgr.data.daily

import android.content.Context
import com.seagori.hgr.domain.DailyTaskState
import com.seagori.hgr.reminder.DailyProgressNotification
import com.seagori.hgr.widget.DailyTasksWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * 매일 할일 목록과 일자별 달성률(%)을 저장한다.
 * 기존 실행 기록(ExecutionEntity)과 완전히 분리되어 있으며, 날짜가 바뀌면 완료 체크는 초기화된다.
 */
class DailyTaskStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("startcoach_daily_tasks", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DailyTaskState> = _state

    private fun load(): DailyTaskState {
        val raw = prefs.getString(KEY_STATE, "").orEmpty()
        if (raw.isBlank()) return DailyTaskState()
        return runCatching {
            json.decodeFromString(DailyTaskState.serializer(), raw)
        }.getOrDefault(DailyTaskState())
    }

    private fun update(transform: (DailyTaskState) -> DailyTaskState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit().putString(KEY_STATE, json.encodeToString(DailyTaskState.serializer(), next)).apply()
        DailyTasksWidget.updateAllWidgets(appContext)
        DailyProgressNotification.update(appContext)
    }

    fun addTask(title: String) = update { it.addTask(title) }

    fun deleteTask(taskId: Long) = update { it.deleteTask(taskId) }

    fun toggleTask(taskId: Long) = update { it.toggleTask(taskId) }

    private companion object {
        const val KEY_STATE = "daily_task_state"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
