package com.seagori.hgr.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.repository.CoachRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArchiveUi(
    val goals: List<GoalEntity> = emptyList(),
    val records: List<ExecutionEntity> = emptyList(),
    val goalTitles: Map<Long, String> = emptyMap()
)

class ArchiveViewModel(
    private val repository: CoachRepository
) : ViewModel() {
    val ui: StateFlow<ArchiveUi> = combine(
        repository.archivedGoals,
        repository.archivedExecutions,
        repository.goals
    ) { goals, records, allGoals ->
        ArchiveUi(
            goals = goals,
            records = records,
            goalTitles = allGoals.associate { it.id to it.title }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUi())

    fun unarchiveGoal(goal: GoalEntity) {
        viewModelScope.launch { repository.unarchiveGoal(goal) }
    }

    fun unarchiveRecord(item: ExecutionEntity) {
        viewModelScope.launch { repository.unarchiveExecution(item) }
    }
}
