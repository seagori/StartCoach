package com.seagori.hgr.ui.goals

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.MaterialEntity
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.repository.GoalPathUi
import com.seagori.hgr.domain.GoalStatus
import com.seagori.hgr.domain.toUserMessage
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalDetailUi(
    val materials: List<MaterialEntity> = emptyList(),
    val progressLine: String? = null,
    val path: GoalPathUi? = null,
    val importMessage: String? = null,
    val pathMessage: String? = null,
    val pathLoading: Boolean = false
)

class GoalsViewModel(
    private val repository: CoachRepository
) : ViewModel() {
    val goals: StateFlow<List<GoalEntity>> = repository.visibleGoals.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val pathSummaries: StateFlow<Map<Long, GoalPathUi>> = combine(
        repository.visiblePaths,
        repository.visibleSteps
    ) { paths, steps ->
        paths.associate { path ->
            path.goalId to GoalPathUi(
                path = path,
                steps = steps.filter { it.pathId == path.id }.sortedBy { it.sortOrder }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val selectedId = MutableStateFlow<Long?>(null)
    private val _detail = MutableStateFlow(GoalDetailUi())

    @OptIn(ExperimentalCoroutinesApi::class)
    val relatedRecords: StateFlow<List<ExecutionEntity>> = selectedId.flatMapLatest { id ->
        repository.recordsFor(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val detail: StateFlow<GoalDetailUi> = _detail

    fun select(id: Long?) {
        selectedId.value = id
        if (id != null) {
            _detail.value = GoalDetailUi()
            refreshMaterials(id)
        }
    }

    fun refreshMaterials(goalId: Long) {
        viewModelScope.launch {
            val materials = repository.materialsForGoal(goalId)
            val path = repository.pathForGoal(goalId)
            _detail.value = _detail.value.copy(
                materials = materials,
                progressLine = repository.curriculumProgressLine(goalId),
                path = path,
                importMessage = null
            )
        }
    }

    fun save(id: Long?, title: String, target: String?, status: GoalStatus) {
        viewModelScope.launch {
            repository.upsertGoal(id, title, target, status)
        }
    }

    fun delete(goal: GoalEntity) {
        viewModelScope.launch { repository.deleteGoal(goal) }
    }

    fun archive(goal: GoalEntity) {
        viewModelScope.launch { repository.archiveGoal(goal) }
    }

    fun importMarkdown(goalId: Long, uri: Uri) {
        viewModelScope.launch {
            val result = repository.importMarkdown(goalId, uri)
            _detail.value = _detail.value.copy(
                importMessage = result.fold(
                    onSuccess = { AppLocale.strings.materialImported },
                    onFailure = { it.toUserMessage(AppLocale.strings.importFailed) }
                )
            )
            refreshMaterials(goalId)
        }
    }

    fun deleteMaterial(materialId: Long, goalId: Long) {
        viewModelScope.launch {
            repository.deleteMaterial(materialId)
            refreshMaterials(goalId)
        }
    }

    /** 현재 입력값을 먼저 저장한 뒤 코치 모델로 경로를 설계한다. */
    fun saveAndBuildPath(goalId: Long, title: String, target: String?, status: GoalStatus) {
        if (_detail.value.pathLoading) return
        viewModelScope.launch {
            _detail.value = _detail.value.copy(pathLoading = true, pathMessage = null)
            val result = runCatching {
                repository.upsertGoal(goalId, title, target, status)
                repository.designPath(goalId).getOrThrow()
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
            }
            _detail.value = _detail.value.copy(
                pathLoading = false,
                pathMessage = result.fold(
                    onSuccess = { AppLocale.strings.pathBuilt },
                    onFailure = { it.toUserMessage(AppLocale.strings.pathBuildFailed) }
                )
            )
            refreshMaterials(goalId)
        }
    }
}
