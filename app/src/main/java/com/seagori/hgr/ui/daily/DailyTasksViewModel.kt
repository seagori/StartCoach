package com.seagori.hgr.ui.daily

import androidx.lifecycle.ViewModel
import com.seagori.hgr.data.daily.DailyTaskStore
import com.seagori.hgr.domain.DailyTaskState
import kotlinx.coroutines.flow.StateFlow

class DailyTasksViewModel(
    private val store: DailyTaskStore
) : ViewModel() {
    val state: StateFlow<DailyTaskState> = store.state

    fun addTask(title: String) = store.addTask(title)

    fun toggleTask(taskId: Long) = store.toggleTask(taskId)

    fun deleteTask(taskId: Long) = store.deleteTask(taskId)
}
