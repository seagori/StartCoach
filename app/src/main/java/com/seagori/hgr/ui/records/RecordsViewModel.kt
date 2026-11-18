package com.seagori.hgr.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.LearningPathEntity
import com.seagori.hgr.data.local.entity.LearningStepEntity
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.domain.ActivityCalendar
import com.seagori.hgr.domain.ActivityEvent
import com.seagori.hgr.domain.ActivitySummary
import com.seagori.hgr.domain.GrowthStats
import com.seagori.hgr.domain.Outcome
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PathSummary(
    val path: LearningPathEntity,
    val completed: Int,
    val total: Int,
    val currentStep: String?
)

data class RecordsUi(
    val items: List<ExecutionEntity> = emptyList(),
    val groupedItems: Map<String, List<ExecutionEntity>> = emptyMap(),
    val goals: List<GoalEntity> = emptyList(),
    val goalTitles: Map<Long, GoalEntity> = emptyMap(),
    val stepTitles: Map<Long, String> = emptyMap(),
    val steps: List<LearningStepEntity> = emptyList(),
    val paths: List<PathSummary> = emptyList(),
    val stats: GrowthStats = GrowthStats(0, 0f, 0, 0, 0),
    val activity: ActivitySummary = ActivitySummary(),
    val completedSteps: Int = 0,
    val totalSteps: Int = 0
)

class RecordsViewModel(
    private val repository: CoachRepository
) : ViewModel() {
    private val dayFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)

    val ui: StateFlow<RecordsUi> = combine(
        repository.visibleExecutions,
        repository.visibleGoals,
        repository.visiblePaths,
        repository.visibleSteps
    ) { history, goals, paths, steps ->
        val stepsByPath = steps.groupBy { it.pathId }
        val summaries = paths.mapNotNull { path ->
            val pathSteps = stepsByPath[path.id].orEmpty().sortedBy { it.sortOrder }
            if (pathSteps.isEmpty()) return@mapNotNull null
            PathSummary(
                path = path,
                completed = pathSteps.count { it.completed },
                total = pathSteps.size,
                currentStep = pathSteps.firstOrNull { !it.completed }?.title
            )
        }
        val grouped = history.groupBy { synchronized(dayFormat) { dayFormat.format(Date(it.createdAt)) } }
        val goalMap = goals.associateBy { it.id }
        val stepMap = steps.associate { it.id to it.title }

        RecordsUi(
            items = history,
            groupedItems = grouped,
            goals = goals,
            goalTitles = goalMap,
            stepTitles = stepMap,
            steps = steps,
            paths = summaries,
            stats = repository.growth(history),
            activity = ActivityCalendar.build(
                history.map {
                    ActivityEvent(
                        timestamp = it.createdAt,
                        done = it.outcome == Outcome.DONE.name,
                        partial = it.outcome == Outcome.PARTIAL.name,
                        durationSec = it.durationSec
                    )
                }
            ),
            completedSteps = steps.count { it.completed },
            totalSteps = steps.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUi())

    fun delete(item: ExecutionEntity) {
        viewModelScope.launch { repository.deleteExecution(item) }
    }

    fun archive(item: ExecutionEntity) {
        viewModelScope.launch { repository.archiveExecution(item) }
    }
}
