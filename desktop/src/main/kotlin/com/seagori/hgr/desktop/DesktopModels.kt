package com.seagori.hgr.desktop

import com.seagori.hgr.domain.DailyTaskState
import com.seagori.hgr.domain.GoalStatus
import kotlinx.serialization.Serializable

@Serializable
data class DesktopGoal(
    val id: Long,
    val title: String,
    val domain: String = "OTHER",
    val status: String = GoalStatus.ACTIVE.name,
    val longTermTarget: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class DesktopSession(
    val id: Long,
    val goalId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val phase: String,
    val difficultyLevel: Int,
    val selectedSuggestionId: Long?
)

@Serializable
data class DesktopSuggestion(
    val id: Long,
    val sessionId: Long,
    val goalId: Long,
    val text: String,
    val difficulty: Int,
    val kind: String,
    val selected: Boolean,
    val source: String,
    val createdAt: Long
)

@Serializable
data class DesktopExecution(
    val id: Long,
    val sessionId: Long,
    val goalId: Long,
    val suggestionId: Long,
    val actionText: String,
    val outcome: String,
    val attempted: Boolean,
    val count: Int?,
    val durationSec: Int?,
    val feltDifficulty: Int?,
    val isEstimated: Boolean,
    val createdAt: Long,
    val stepId: Long? = null,
    val archived: Boolean = false,
    val updatedAt: Long = createdAt
)

@Serializable
data class DesktopMemory(
    val goalId: Long,
    val preference: String? = null,
    val difficultyNote: String? = null,
    val lastProgress: String? = null,
    val summary: String? = null,
    val updatedAt: Long
)

@Serializable
data class DesktopMaterial(
    val id: Long,
    val goalId: Long,
    val title: String,
    val markdown: String,
    val markdownHash: String,
    val importedAt: Long
)

@Serializable
data class DesktopChunk(
    val id: Long,
    val materialId: Long,
    val headingPath: String,
    val body: String,
    val sortOrder: Int
)

@Serializable
data class DesktopCurriculumProgress(
    val id: Long,
    val goalId: Long,
    val materialId: Long,
    val chunkIndex: Int,
    val updatedAt: Long
)

@Serializable
data class DesktopPath(
    val id: Long,
    val goalId: Long,
    val title: String,
    val sourceMaterialId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class DesktopStep(
    val id: Long,
    val pathId: Long,
    val sortOrder: Int,
    val title: String,
    val instruction: String,
    val criteria: String,
    val minutes: Int,
    val difficulty: Int,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sourceHeading: String? = null
) {
    val criteriaList: List<String>
        get() = criteria.split('\n').map { it.trim() }.filter { it.isNotBlank() }
}

@Serializable
data class DesktopCachedChoice(
    val text: String,
    val difficulty: Int
)

@Serializable
data class DesktopData(
    val goals: List<DesktopGoal> = emptyList(),
    val sessions: List<DesktopSession> = emptyList(),
    val suggestions: List<DesktopSuggestion> = emptyList(),
    val executions: List<DesktopExecution> = emptyList(),
    val memories: List<DesktopMemory> = emptyList(),
    val materials: List<DesktopMaterial> = emptyList(),
    val chunks: List<DesktopChunk> = emptyList(),
    val curriculumProgress: List<DesktopCurriculumProgress> = emptyList(),
    val paths: List<DesktopPath> = emptyList(),
    val steps: List<DesktopStep> = emptyList(),
    val cachedChoices: Map<Long, List<DesktopCachedChoice>> = emptyMap(),
    val dailyTasks: DailyTaskState = DailyTaskState()
)

data class DesktopPathUi(
    val path: DesktopPath,
    val steps: List<DesktopStep>,
    val current: DesktopStep?
) {
    val completed: Int get() = steps.count { it.completed }
    val total: Int get() = steps.size
}

data class DesktopPathSummary(
    val path: DesktopPath,
    val goalTitle: String,
    val completed: Int,
    val total: Int,
    val currentStep: String?
)
