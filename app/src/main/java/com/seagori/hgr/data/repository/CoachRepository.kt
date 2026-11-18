package com.seagori.hgr.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.seagori.hgr.data.backup.BackupManager
import com.seagori.hgr.data.cache.SuggestionCacheStore
import com.seagori.hgr.data.curriculum.CurriculumMemoryService
import com.seagori.hgr.data.io.SizeLimits
import com.seagori.hgr.data.io.readLimited
import com.seagori.hgr.data.local.AppDatabase
import com.seagori.hgr.data.local.dao.ExecutionDao
import com.seagori.hgr.data.local.dao.GoalDao
import com.seagori.hgr.data.local.dao.LearningPathDao
import com.seagori.hgr.data.local.dao.LearningStepDao
import com.seagori.hgr.data.local.dao.MemoryDao
import com.seagori.hgr.data.local.dao.SessionDao
import com.seagori.hgr.data.local.dao.SuggestionDao
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.LearningPathEntity
import com.seagori.hgr.data.local.entity.LearningStepEntity
import com.seagori.hgr.data.local.entity.MaterialEntity
import com.seagori.hgr.data.local.entity.MemoryEntity
import com.seagori.hgr.data.local.entity.SessionEntity
import com.seagori.hgr.data.local.entity.SuggestionEntity
import com.seagori.hgr.data.remote.LlmSuggestionGateway
import com.seagori.hgr.data.settings.SecureTokenStore
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.Choice
import com.seagori.hgr.domain.Clarification
import com.seagori.hgr.domain.GoalDomain
import com.seagori.hgr.domain.GoalDomains
import com.seagori.hgr.domain.GoalStatus
import com.seagori.hgr.domain.GrowthStats
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.Outcome
import com.seagori.hgr.domain.PathPlan
import com.seagori.hgr.domain.PathPlanRequest
import com.seagori.hgr.domain.SessionPhase
import com.seagori.hgr.domain.SessionRestoreRoute
import com.seagori.hgr.domain.SessionStateMachine
import com.seagori.hgr.domain.SuggestionContext
import com.seagori.hgr.domain.SuggestionKind
import com.seagori.hgr.domain.SuggestionResult
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.domain.isAttempted
import com.seagori.hgr.domain.label
import com.seagori.hgr.domain.maskApiKey
import com.seagori.hgr.domain.toUserMessage
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.shortcut.GoalShortcutHelper
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeGoalItem(
    val goal: GoalEntity,
    val attemptCount: Int,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val currentStepTitle: String? = null,
    val easiestCachedAction: String? = null
) {
    val progress: Float
        get() = if (totalSteps <= 0) 0f else completedSteps.toFloat() / totalSteps
}

data class GoalPathUi(
    val path: LearningPathEntity,
    val steps: List<LearningStepEntity>
) {
    val completed: Int get() = steps.count { it.completed }
    val total: Int get() = steps.size
    val current: LearningStepEntity? get() = steps.firstOrNull { !it.completed }
}

data class SessionUi(
    val session: SessionEntity,
    val goal: GoalEntity,
    val choices: List<SuggestionEntity>,
    val selectedId: Long?,
    val lastAction: String?,
    val curriculumLabel: String? = null,
    val error: String? = null,
    val loading: Boolean = false,
    val clarification: Clarification? = null,
    val clarificationAnswer: String? = null,
    val step: LearningStepEntity? = null,
    val stepIndex: Int = 0,
    val stepCount: Int = 0,
    val materialExcerpt: String? = null
)

sealed interface NextStepResult {
    data object Continue : NextStepResult
    data object PathDone : NextStepResult
    data object Ended : NextStepResult
}

class CoachRepository(
    private val db: AppDatabase,
    private val goalDao: GoalDao,
    private val sessionDao: SessionDao,
    private val suggestionDao: SuggestionDao,
    private val executionDao: ExecutionDao,
    private val memoryDao: MemoryDao,
    private val pathDao: LearningPathDao,
    private val stepDao: LearningStepDao,
    private val llm: LlmSuggestionGateway,
    private val curriculum: CurriculumMemoryService,
    private val backup: BackupManager,
    private val settings: SettingsStore,
    private val secureTokens: SecureTokenStore,
    private val suggestionCache: SuggestionCacheStore,
    private val appContext: Context,
    private val onResetSyncIndex: () -> Unit = {},
    private val onTriggerRefresh: () -> Unit = {}
) {
    private val sessionUi = MutableStateFlow<SessionUi?>(null)
    val session: StateFlow<SessionUi?> = sessionUi
    private val suggestionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var suggestionJob: Job? = null

    val goals: Flow<List<GoalEntity>> = goalDao.observeAll()
    val visibleGoals: Flow<List<GoalEntity>> = goalDao.observeVisible()
    val archivedGoals: Flow<List<GoalEntity>> = goalDao.observeArchived()
    val visibleExecutions: Flow<List<ExecutionEntity>> = executionDao.observeVisible()
    val archivedExecutions: Flow<List<ExecutionEntity>> = executionDao.observeArchived()
    val visiblePaths: Flow<List<LearningPathEntity>> = pathDao.observeForVisibleGoals()
    val visibleSteps: Flow<List<LearningStepEntity>> = stepDao.observeForVisibleGoals()

    val homeGoals: Flow<List<HomeGoalItem>> = combine(
        goalDao.observeActive(),
        executionDao.observeAttemptCounts(),
        pathDao.observeForActiveGoals(),
        stepDao.observeForActiveGoals(),
        suggestionCache.version
    ) { goals, attemptCounts, allPaths, allSteps, _ ->
        val attemptsByGoal = attemptCounts.associate { it.goalId to it.count }
        val pathByGoal = allPaths.associateBy { it.goalId }
        val stepsByPath = allSteps.groupBy { it.pathId }
        goals.map { goal ->
            val path = pathByGoal[goal.id]
            val goalSteps = path?.let { stepsByPath[it.id] }.orEmpty()
            HomeGoalItem(
                goal = goal,
                attemptCount = attemptsByGoal[goal.id] ?: 0,
                completedSteps = goalSteps.count { it.completed },
                totalSteps = goalSteps.size,
                currentStepTitle = goalSteps.firstOrNull { !it.completed }?.title,
                easiestCachedAction = suggestionCache.easiestAction(goal.id)
            )
        }
    }

    suspend fun bootstrap(): SessionRestoreRoute? {
        restoreOpenSession()
        val ui = sessionUi.value ?: return null
        return SessionStateMachine.restoreRoute(SessionPhase.valueOf(ui.session.phase))
    }

    suspend fun restoreOpenSession() {
        val open = sessionDao.getOpen() ?: return
        val goal = goalDao.getById(open.goalId) ?: return
        val choices = suggestionDao.listForSession(open.id)
        val path = pathState(goal.id)
        val step = path?.current
        val materialExcerpt = step?.let { curriculum.excerptForHeading(goal.id, it.sourceHeading) }
        val curriculumLabel = curriculum.progressLine(goal.id)
        sessionUi.value = SessionUi(
            session = open,
            goal = goal,
            choices = choices,
            selectedId = open.selectedSuggestionId ?: choices.firstOrNull()?.id,
            lastAction = choices.firstOrNull { it.selected }?.text,
            curriculumLabel = curriculumLabel,
            step = step,
            stepIndex = stepPosition(path, step),
            stepCount = path?.total ?: 0,
            materialExcerpt = materialExcerpt
        )
    }

    private suspend fun pathState(goalId: Long): GoalPathUi? {
        val path = pathDao.getByGoal(goalId) ?: return null
        return GoalPathUi(path, stepDao.listForPath(path.id))
    }

    suspend fun pathForGoal(goalId: Long): GoalPathUi? = pathState(goalId)

    private fun stepPosition(path: GoalPathUi?, step: LearningStepEntity?): Int {
        if (path == null || step == null) return 0
        return path.steps.indexOfFirst { it.id == step.id }.plus(1).coerceAtLeast(0)
    }

    suspend fun startOrContinue(rawTitle: String): Long {
        val title = rawTitle.trim()
        require(title.isNotEmpty())
        val now = System.currentTimeMillis()
        val goal = goalDao.getByTitle(title) ?: goalDao.getById(
            goalDao.insert(
                GoalEntity(
                    title = title,
                    domain = GoalDomains.infer(title).name,
                    status = GoalStatus.ACTIVE.name,
                    longTermTarget = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )!!
        if (goal.status != GoalStatus.ACTIVE.name && goal.status != GoalStatus.ARCHIVED.name) {
            goalDao.update(goal.copy(status = GoalStatus.ACTIVE.name, updatedAt = now))
        }
        return startSession(goal.id, lowered = false, lastOutcome = null)
    }

    suspend fun startSession(
        goalId: Long,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String? = null,
        skipCache: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        sessionDao.getOpen()?.let { open ->
            sessionDao.update(open.copy(endedAt = now, phase = SessionPhase.ENDED.name))
        }
        val goal = goalDao.getById(goalId) ?: error(AppLocale.strings.goalNotFound)
        val path = pathState(goal.id)
        val step = path?.current
        val materialExcerpt = step?.let { curriculum.excerptForHeading(goal.id, it.sourceHeading) }
        val cached = if (!skipCache && clarification == null && lastOutcome == null) {
            suggestionCache.get(goal.id)
        } else {
            emptyList()
        }
        val session = SessionEntity(
            goalId = goal.id,
            startedAt = now,
            endedAt = null,
            phase = SessionStateMachine.phaseForNewSuggestions(lastOutcome).name,
            difficultyLevel = if (lowered) 0 else 1,
            selectedSuggestionId = null
        )
        val sessionId = sessionDao.insert(session)
        suggestionJob?.cancel()
        goalDao.update(goal.copy(updatedAt = now))
        if (cached.isNotEmpty()) {
            val saved = persistChoices(sessionId, goal.id, cached)
            val label = curriculum.progressLine(goal.id)
            sessionUi.value = SessionUi(
                session = session.copy(id = sessionId),
                goal = goal,
                choices = saved,
                selectedId = saved.firstOrNull()?.id,
                lastAction = null,
                curriculumLabel = label,
                loading = false,
                clarificationAnswer = clarification,
                step = step,
                stepIndex = stepPosition(path, step),
                stepCount = path?.total ?: 0,
                materialExcerpt = materialExcerpt
            )
            onTriggerRefresh()
            return sessionId
        }
        sessionUi.value = SessionUi(
            session = session.copy(id = sessionId),
            goal = goal,
            choices = emptyList(),
            selectedId = null,
            lastAction = null,
            loading = true,
            clarificationAnswer = clarification,
            step = step,
            stepIndex = stepPosition(path, step),
            stepCount = path?.total ?: 0,
            materialExcerpt = materialExcerpt
        )
        launchChoicesLoad(
            sessionId = sessionId,
            goal = goal,
            step = step,
            lowered = lowered,
            lastOutcome = lastOutcome,
            clarification = clarification,
            forceChoices = clarification != null
        )
        onTriggerRefresh()
        return sessionId
    }

    suspend fun openGoalFromTrigger(goalId: Long): SessionRestoreRoute? {
        val goal = goalDao.getById(goalId) ?: return null
        if (goal.status == GoalStatus.ARCHIVED.name) return null
        val open = sessionDao.getOpen()
        if (open != null && open.goalId == goalId) {
            val route = SessionStateMachine.restoreRoute(
                runCatching { SessionPhase.valueOf(open.phase) }.getOrDefault(SessionPhase.ENDED)
            )
            if (route == SessionRestoreRoute.CHECK) {
                restoreOpenSession()
                return SessionRestoreRoute.CHECK
            }
        }
        startSession(goalId, lowered = false, lastOutcome = null)
        return SessionRestoreRoute.SUGGESTION
    }

    suspend fun startQuickAction(goalId: Long, actionText: String): SessionRestoreRoute {
        val trimmed = actionText.trim()
        if (trimmed.isEmpty()) {
            startSession(goalId, lowered = true, lastOutcome = null)
            return SessionRestoreRoute.SUGGESTION
        }
        val now = System.currentTimeMillis()
        sessionDao.getOpen()?.let { open ->
            sessionDao.update(open.copy(endedAt = now, phase = SessionPhase.ENDED.name))
        }
        val goal = goalDao.getById(goalId) ?: error(AppLocale.strings.goalNotFound)
        val path = pathState(goal.id)
        val step = path?.current
        val materialExcerpt = step?.let { curriculum.excerptForHeading(goal.id, it.sourceHeading) }
        val session = SessionEntity(
            goalId = goal.id,
            startedAt = now,
            endedAt = null,
            phase = SessionPhase.CHECKING.name,
            difficultyLevel = 0,
            selectedSuggestionId = null
        )
        val sessionId = sessionDao.insert(session)
        suggestionJob?.cancel()
        val suggestion = SuggestionEntity(
            sessionId = sessionId,
            goalId = goal.id,
            text = trimmed,
            difficulty = 0,
            kind = SuggestionKind.ACTION.name,
            selected = true,
            source = SOURCE_REMOTE,
            createdAt = now
        )
        val suggestionId = suggestionDao.insertAll(listOf(suggestion)).first()
        val updatedSession = session.copy(id = sessionId, selectedSuggestionId = suggestionId)
        sessionDao.update(updatedSession)
        goalDao.update(goal.copy(updatedAt = now))
        sessionUi.value = SessionUi(
            session = updatedSession,
            goal = goal,
            choices = listOf(suggestion.copy(id = suggestionId)),
            selectedId = suggestionId,
            lastAction = trimmed,
            curriculumLabel = curriculum.progressLine(goal.id),
            loading = false,
            step = step,
            stepIndex = stepPosition(path, step),
            stepCount = path?.total ?: 0,
            materialExcerpt = materialExcerpt
        )
        onTriggerRefresh()
        return SessionRestoreRoute.CHECK
    }

    private fun launchChoicesLoad(
        sessionId: Long,
        goal: GoalEntity,
        step: LearningStepEntity?,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String?,
        forceChoices: Boolean
    ) {
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val loaded = try {
                loadChoices(goal, step, lowered, lastOutcome, clarification, forceChoices)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportSuggestionFailure(sessionId, e)
                return@launch
            }
            if (sessionUi.value?.session?.id != sessionId) return@launch
            if (loaded.clarification != null) {
                sessionUi.update { current ->
                    if (current?.session?.id != sessionId) return@update current
                    current.copy(
                        choices = emptyList(),
                        selectedId = null,
                        clarification = loaded.clarification,
                        clarificationAnswer = clarification,
                        curriculumLabel = loaded.curriculumLabel,
                        error = loaded.remoteError,
                        loading = false
                    )
                }
                return@launch
            }
            val saved = try {
                persistChoices(sessionId, goal.id, loaded.choices)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                reportSuggestionFailure(sessionId, e)
                return@launch
            }
            if (loaded.choices.isNotEmpty()) {
                suggestionCache.save(goal.id, loaded.choices)
                onTriggerRefresh()
            }
            sessionUi.update { current ->
                if (current?.session?.id != sessionId) return@update current
                current.copy(
                    choices = saved,
                    selectedId = saved.firstOrNull()?.id,
                    clarification = null,
                    clarificationAnswer = clarification,
                    curriculumLabel = loaded.curriculumLabel,
                    error = loaded.remoteError,
                    loading = false
                )
            }
        }
    }

    private fun reportSuggestionFailure(sessionId: Long, throwable: Throwable) {
        sessionUi.update { current ->
            if (current?.session?.id != sessionId) return@update current
            current.copy(
                loading = false,
                error = throwable.toUserMessage(AppLocale.strings.serverConnectionFailed)
            )
        }
    }

    suspend fun answerClarification(option: String) {
        val ui = sessionUi.value ?: return
        val clarification = ui.clarification ?: return
        val trimmed = option.trim()
        if (trimmed.isEmpty()) return
        val answer = "${clarification.question} → $trimmed"
        suggestionJob?.cancel()
        sessionUi.value = ui.copy(
            choices = emptyList(),
            selectedId = null,
            clarification = null,
            clarificationAnswer = answer,
            loading = true,
            error = null
        )
        launchChoicesLoad(
            sessionId = ui.session.id,
            goal = ui.goal,
            step = ui.step,
            lowered = ui.session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = answer,
            forceChoices = true
        )
    }

    suspend fun skipClarification() {
        val ui = sessionUi.value ?: return
        if (ui.clarification == null) return
        suggestionJob?.cancel()
        sessionUi.value = ui.copy(
            choices = emptyList(),
            selectedId = null,
            clarification = null,
            loading = true,
            error = null
        )
        launchChoicesLoad(
            sessionId = ui.session.id,
            goal = ui.goal,
            step = ui.step,
            lowered = ui.session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = ui.clarificationAnswer,
            forceChoices = true
        )
    }

    suspend fun selectChoice(suggestionId: Long) {
        sessionUi.update { current -> current?.copy(selectedId = suggestionId) }
    }

    suspend fun reloadSuggestions(): Long? {
        val ui = sessionUi.value ?: return null
        return startSession(
            goalId = ui.goal.id,
            lowered = ui.session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = ui.clarificationAnswer,
            skipCache = true
        )
    }

    suspend fun confirmCustomAction(text: String): ConfirmResult {
        val ui = sessionUi.value ?: return ConfirmResult.Stay
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ConfirmResult.Stay
        suggestionJob?.cancel()
        suggestionJob = null
        val now = System.currentTimeMillis()
        val entity = SuggestionEntity(
            sessionId = ui.session.id,
            goalId = ui.goal.id,
            text = trimmed,
            difficulty = ui.session.difficultyLevel,
            kind = SuggestionKind.ACTION.name,
            selected = true,
            source = SOURCE_MANUAL,
            createdAt = now
        )
        val id = suggestionDao.insertAll(listOf(entity)).first()
        val session = ui.session.copy(
            phase = SessionStateMachine.afterSuggestionChosen(SuggestionKind.ACTION).name,
            selectedSuggestionId = id
        )
        sessionDao.update(session)
        sessionUi.value = ui.copy(
            session = session,
            lastAction = trimmed,
            selectedId = id,
            loading = false
        )
        return ConfirmResult.Check
    }

    suspend fun confirmSelection(): ConfirmResult {
        val ui = sessionUi.value ?: return ConfirmResult.Stay
        val chosen = ui.choices.firstOrNull { it.id == ui.selectedId }
            ?: ui.choices.firstOrNull()
            ?: return ConfirmResult.Stay
        return when (val kind = SuggestionKind.valueOf(chosen.kind)) {
            SuggestionKind.END -> ConfirmResult.Home.also { endSession() }
            SuggestionKind.LOWER, SuggestionKind.ACTION -> {
                suggestionDao.update(chosen.copy(selected = true))
                val session = ui.session.copy(
                    phase = SessionStateMachine.afterSuggestionChosen(SuggestionKind.ACTION).name,
                    selectedSuggestionId = chosen.id
                )
                sessionDao.update(session)
                sessionUi.value = ui.copy(
                    session = session,
                    lastAction = chosen.text,
                    selectedId = chosen.id,
                    loading = false
                )
                ConfirmResult.Check
            }
        }
    }

    suspend fun recordOutcome(outcome: Outcome): String {
        val ui = sessionUi.value ?: return AppLocale.strings.sessionMissing
        val now = System.currentTimeMillis()
        val attempted = outcome.isAttempted
        val action = ui.lastAction ?: ui.choices.firstOrNull { it.selected }?.text.orEmpty()
        val step = ui.step
        val phase = SessionStateMachine.afterCheck(outcome)
        val updatedSession = ui.session.copy(phase = phase.name, endedAt = if (phase == SessionPhase.ENDED) now else null)
        val updatedStep = if (outcome == Outcome.DONE && step != null) {
            step.copy(completed = true, completedAt = now)
        } else {
            step
        }

        db.withTransaction {
            executionDao.insert(
                ExecutionEntity(
                    sessionId = ui.session.id,
                    goalId = ui.goal.id,
                    suggestionId = ui.session.selectedSuggestionId ?: 0L,
                    actionText = action,
                    outcome = outcome.name,
                    attempted = attempted,
                    count = if (attempted) 1 else 0,
                    durationSec = null,
                    feltDifficulty = ui.session.difficultyLevel,
                    isEstimated = false,
                    createdAt = now,
                    stepId = step?.id
                )
            )
            val memory = memoryDao.getByGoal(ui.goal.id)
            memoryDao.upsert(
                MemoryEntity(
                    id = memory?.id ?: 0,
                    goalId = ui.goal.id,
                    preference = memory?.preference,
                    difficultyNote = if (outcome == Outcome.NONE) AppLocale.strings.difficultyLowerNeeded else memory?.difficultyNote,
                    lastProgress = action,
                    summary = AppLocale.strings.memorySummary(ui.goal.title, outcome.label()),
                    updatedAt = now
                )
            )
            if (outcome == Outcome.DONE && step != null) {
                stepDao.update(updatedStep!!)
            }
            sessionDao.update(updatedSession)
            curriculum.advanceAfterOutcome(ui.goal.id, outcome)
        }

        sessionUi.value = ui.copy(
            session = updatedSession,
            step = updatedStep
        )
        prefetchLightChoices(ui.goal)
        return if (attempted) {
            AppLocale.strings.outcomeAttempted(action)
        } else {
            AppLocale.strings.outcomeNotAttempted
        }
    }

    suspend fun nextAfterCheck(): NextStepResult {
        val ui = sessionUi.value ?: return NextStepResult.Ended
        val outcome = executionDao.latestForSession(ui.session.id)?.outcome?.let { Outcome.valueOf(it) }
        if (outcome == Outcome.DONE) {
            val path = pathState(ui.goal.id)
            if (path != null && path.total > 0 && path.current == null) {
                endSession()
                return NextStepResult.PathDone
            }
        }
        startSession(
            ui.goal.id,
            lowered = SessionStateMachine.shouldLower(outcome),
            lastOutcome = outcome,
            clarification = ui.clarificationAnswer
        )
        return NextStepResult.Continue
    }

    suspend fun endSession() {
        suggestionJob?.cancel()
        suggestionJob = null
        val ui = sessionUi.value ?: return
        sessionDao.update(ui.session.copy(endedAt = System.currentTimeMillis(), phase = SessionPhase.ENDED.name))
        sessionUi.value = null
        prefetchLightChoices(ui.goal)
    }

    private fun prefetchLightChoices(goal: GoalEntity) {
        if (!llm.isRemoteEnabled() && !llm.isCoachConfigured()) return
        suggestionScope.launch {
            runCatching {
                val path = pathState(goal.id)
                val step = path?.current
                val loaded = loadChoices(
                    goal = goal,
                    step = step,
                    lowered = true,
                    lastOutcome = null,
                    clarification = null,
                    forceChoices = true
                )
                if (loaded.choices.isNotEmpty()) {
                    suggestionCache.save(goal.id, loaded.choices)
                    onTriggerRefresh()
                }
            }
        }
    }

    suspend fun upsertGoal(id: Long?, title: String, target: String?, status: GoalStatus): Long {
        val now = System.currentTimeMillis()
        val trimmed = title.trim()
        require(trimmed.isNotEmpty())
        val targetValue = target?.trim()?.ifBlank { null }
        val current = if (id == null) {
            return goalDao.insert(
                GoalEntity(
                    title = trimmed,
                    domain = GoalDomains.infer(trimmed).name,
                    status = status.name,
                    longTermTarget = targetValue,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            goalDao.getById(id) ?: error(AppLocale.strings.goalMissing)
        }
        goalDao.update(
            current.copy(
                title = trimmed,
                domain = current.domain,
                status = status.name,
                longTermTarget = targetValue,
                updatedAt = now
            )
        )
        return current.id
    }

    suspend fun deleteGoal(goal: GoalEntity) {
        if (sessionUi.value?.goal?.id == goal.id) sessionUi.value = null
        suggestionCache.remove(goal.id)
        GoalShortcutHelper.disableGoalShortcut(appContext, goal.id)
        goalDao.delete(goal)
        onTriggerRefresh()
    }

    suspend fun archiveGoal(goal: GoalEntity) {
        val now = System.currentTimeMillis()
        if (sessionUi.value?.goal?.id == goal.id) {
            endSession()
        }
        db.withTransaction {
            goalDao.update(goal.copy(status = GoalStatus.ARCHIVED.name, updatedAt = now))
            executionDao.setArchivedForGoal(goal.id, true, now)
        }
        GoalShortcutHelper.disableGoalShortcut(appContext, goal.id)
        onTriggerRefresh()
    }

    suspend fun unarchiveGoal(goal: GoalEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            goalDao.update(goal.copy(status = GoalStatus.ACTIVE.name, updatedAt = now))
            executionDao.setArchivedForGoal(goal.id, false, now)
        }
        onTriggerRefresh()
    }

    fun recordsFor(goalId: Long?): Flow<List<ExecutionEntity>> {
        return if (goalId == null) visibleExecutions else executionDao.observeByGoal(goalId)
    }

    suspend fun deleteExecution(item: ExecutionEntity) {
        executionDao.delete(item)
    }

    suspend fun archiveExecution(item: ExecutionEntity) {
        executionDao.update(item.copy(archived = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun unarchiveExecution(item: ExecutionEntity) {
        executionDao.update(item.copy(archived = false, updatedAt = System.currentTimeMillis()))
    }

    fun growth(history: List<ExecutionEntity>): GrowthStats {
        val difficulties = history.sortedBy { it.createdAt }.mapNotNull { it.feltDifficulty }
        return GrowthStats(
            firstAttempts = history.count { it.attempted },
            startSuccessRate = if (history.isEmpty()) 0f else {
                history.count { it.outcome in SUCCESS_OUTCOMES } / history.size.toFloat()
            },
            actualVolume = history.filter { !it.isEstimated }.sumOf { it.count ?: 0 },
            estimatedVolume = history.filter { it.isEstimated }.sumOf { it.count ?: 0 },
            difficultyTrend = if (difficulties.size >= 2) difficulties.last() - difficulties.first() else 0
        )
    }

    fun llmSettings(): LlmSettingsUi = LlmSettingsUi(
        fast = profileSettings(LlmProfile.FAST),
        coach = profileSettings(LlmProfile.COACH)
    )

    private fun profileSettings(profile: LlmProfile): LlmProfileUi {
        val url = settings.apiBaseUrl(profile)
        var token = UrlRules.origin(url)?.let { secureTokens.tokenFor(profile, it) }
        if (token == null && profile == LlmProfile.COACH) {
            // 코치 주소가 비어 있으면 런타임에 빠른 제안 설정으로 대체되므로 그 키를 보여준다.
            val fastOrigin = UrlRules.origin(settings.apiBaseUrl(LlmProfile.FAST))
            val coachOrigin = UrlRules.origin(url)
            if (fastOrigin != null && coachOrigin == null) {
                token = secureTokens.tokenFor(LlmProfile.FAST, fastOrigin)
            }
        }
        return LlmProfileUi(
            apiUrl = url,
            modelId = settings.modelId(profile),
            hasSavedToken = token != null,
            maskedToken = token?.let(::maskApiKey).orEmpty(),
            reasoningEffort = settings.coachReasoningEffort
        )
    }

    fun hasSavedToken(profile: LlmProfile): Boolean = secureTokens.hasToken(profile)

    /** @return 토큰 저장 성공 여부. URL 검증은 호출 전에 수행한다. */
    fun saveLlmSettings(ui: LlmSettingsUi): Boolean {
        var tokensSaved = true
        val fastOrigin = UrlRules.origin(ui.fast.apiUrl)
        val coachOrigin = UrlRules.origin(ui.coach.apiUrl)
        val sameOrigin = fastOrigin != null && fastOrigin == coachOrigin
        val fastInput = ui.fast.bearerTokenInput.trim()
        val coachInput = ui.coach.bearerTokenInput.trim()
        val sharedFast = fastInput.ifBlank { fastOrigin?.let { secureTokens.tokenFor(LlmProfile.FAST, it) }.orEmpty() }
        val sharedCoach = coachInput.ifBlank { coachOrigin?.let { secureTokens.tokenFor(LlmProfile.COACH, it) }.orEmpty() }

        listOf(LlmProfile.FAST to ui.fast, LlmProfile.COACH to ui.coach).forEach { (profile, item) ->
            settings.setApiBaseUrl(profile, item.apiUrl)
            settings.setModelId(profile, item.modelId)
            val origin = UrlRules.origin(item.apiUrl)
            val input = item.bearerTokenInput.trim()
            if (input.isNotEmpty()) {
                tokensSaved = tokensSaved && origin != null && secureTokens.save(profile, input, origin)
            } else if (origin != null) {
                val own = secureTokens.tokenFor(profile, origin)
                if (own == null) {
                    // 같은 주소를 쓰는 다른 프로필의 키를 복사해 두 번 입력하지 않게 한다.
                    val other = if (sameOrigin) {
                        when (profile) {
                            LlmProfile.FAST -> sharedCoach
                            LlmProfile.COACH -> sharedFast
                        }
                    } else {
                        ""
                    }
                    if (other.isNotBlank()) {
                        tokensSaved = tokensSaved && secureTokens.save(profile, other, origin)
                    } else {
                        secureTokens.clear(profile)
                    }
                }
            }
        }
        settings.coachReasoningEffort = ui.coach.reasoningEffort
        return tokensSaved
    }

    fun clearToken(profile: LlmProfile) {
        secureTokens.clear(profile)
    }

    fun appLanguage(): AppLanguage = settings.appLanguage

    fun setAppLanguage(language: AppLanguage) {
        settings.appLanguage = language
        AppLocale.set(language)
    }

    suspend fun testLlmConnection(profile: LlmProfile): Result<String> = llm.testConnection(profile)

    suspend fun exportBackup(uri: Uri, password: CharArray): Result<Unit> = backup.exportTo(uri, password)

    suspend fun importBackup(uri: Uri, password: CharArray): Result<Unit> = backup.importReplace(uri, password).onSuccess {
        suggestionJob?.cancel()
        suggestionJob = null
        sessionUi.value = null
        suggestionCache.clearAll()
        onResetSyncIndex()
        onTriggerRefresh()
    }

    suspend fun materialsForGoal(goalId: Long): List<MaterialEntity> = curriculum.materialsForGoal(goalId)

    suspend fun curriculumProgressLine(goalId: Long): String? = curriculum.progressLine(goalId)

    suspend fun importMarkdown(goalId: Long, uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val title = resolveDisplayName(uri) ?: AppLocale.strings.materialFallback
            val markdown = appContext.contentResolver.openInputStream(uri)?.use {
                it.readLimited(SizeLimits.MARKDOWN_BYTES).decodeToString()
            } ?: error(AppLocale.strings.fileReadFailed)
            curriculum.importMarkdown(goalId, title, markdown)
        }
    }

    suspend fun deleteMaterial(materialId: Long) {
        curriculum.deleteMaterial(materialId)
    }

    suspend fun clearAllData() {
        suggestionJob?.cancel()
        suggestionJob = null
        goalDao.deleteAll()
        sessionUi.value = null
        suggestionCache.clearAll()
        GoalShortcutHelper.clearAllShortcuts(appContext)
        onResetSyncIndex()
        onTriggerRefresh()
    }

    suspend fun designPath(goalId: Long): Result<PathPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val goal = goalDao.getById(goalId) ?: error(AppLocale.strings.goalMissing)
            check(llm.isCoachConfigured()) { AppLocale.strings.coachNotConfigured }
            val outline = curriculum.outlineForGoal(goalId)
            val plan = llm.designPath(
                PathPlanRequest(
                    title = goal.title,
                    target = goal.longTermTarget,
                    materialOutline = outline?.text
                )
            ).getOrThrow()
            val now = System.currentTimeMillis()
            db.withTransaction {
                pathDao.getByGoal(goalId)?.let { existing ->
                    stepDao.deleteForPath(existing.id)
                    pathDao.delete(existing)
                }
                val pathId = pathDao.insert(
                    LearningPathEntity(
                        goalId = goalId,
                        title = plan.title,
                        sourceMaterialId = outline?.materialId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                stepDao.insertAll(
                    plan.steps.mapIndexed { index, step ->
                        LearningStepEntity(
                            pathId = pathId,
                            sortOrder = index,
                            title = step.title,
                            instruction = step.instruction,
                            criteria = step.criteria.joinToString("\n"),
                            minutes = step.minutes,
                            difficulty = step.difficulty,
                            sourceHeading = step.sourceHeading
                        )
                    }
                )
            }
            plan
        }
    }

    private data class ChoiceLoad(
        val choices: List<Choice> = emptyList(),
        val clarification: Clarification? = null,
        val curriculumLabel: String? = null,
        val remoteError: String? = null
    )

    private suspend fun loadChoices(
        goal: GoalEntity,
        step: LearningStepEntity?,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String?,
        forceChoices: Boolean
    ): ChoiceLoad {
        val memory = memoryDao.getByGoal(goal.id)
        val domain = runCatching { GoalDomain.valueOf(goal.domain) }.getOrDefault(GoalDomain.OTHER)
        val excerpt = curriculum.buildExcerpt(goal.id)
        val label = curriculum.progressLine(goal.id)
        val context = SuggestionContext(
            title = goal.title,
            domain = domain,
            lowered = lowered,
            lastProgress = memory?.lastProgress,
            lastOutcome = lastOutcome,
            curriculumExcerpt = if (step == null) excerpt?.text else null,
            memorySummary = memory?.summary,
            clarification = clarification,
            forceChoices = forceChoices,
            longTermTarget = goal.longTermTarget,
            stepTitle = step?.title,
            stepInstruction = step?.instruction,
            stepCriteria = step?.criteria?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty(),
            stepPosition = step?.let { "${it.sortOrder + 1}" },
            stepMinutes = step?.minutes
        )
        if (!llm.isRemoteEnabled() && !llm.isCoachConfigured()) {
            return ChoiceLoad(
                curriculumLabel = label,
                remoteError = AppLocale.strings.remoteNotConfigured
            )
        }
        val result = if (step != null) llm.fetchCoachChoices(context) else llm.fetchChoices(context)
        val value = result.getOrNull()
        if (value is SuggestionResult.Clarify) {
            return ChoiceLoad(
                clarification = Clarification(value.question, value.options),
                curriculumLabel = label
            )
        }
        val choices = (value as? SuggestionResult.Choices)?.choices.orEmpty()
        if (choices.isEmpty()) {
            val message = result.exceptionOrNull()?.toUserMessage(AppLocale.strings.serverConnectionFailed)
                ?: AppLocale.strings.noSuggestions
            return ChoiceLoad(
                curriculumLabel = label,
                remoteError = AppLocale.strings.retryLater(message)
            )
        }
        return ChoiceLoad(choices = choices, curriculumLabel = label)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)?.removeSuffix(".md")
        }
        return null
    }

    private suspend fun persistChoices(
        sessionId: Long,
        goalId: Long,
        choices: List<Choice>
    ): List<SuggestionEntity> {
        val now = System.currentTimeMillis()
        val entities = choices.map {
            SuggestionEntity(
                sessionId = sessionId,
                goalId = goalId,
                text = it.text,
                difficulty = it.difficulty,
                kind = it.kind.name,
                selected = false,
                source = SOURCE_REMOTE,
                createdAt = now
            )
        }
        return entities.zip(suggestionDao.insertAll(entities)) { entity, id -> entity.copy(id = id) }
    }

    private companion object {
        const val SOURCE_REMOTE = "ai"
        const val SOURCE_MANUAL = "manual"
        val SUCCESS_OUTCOMES = setOf(Outcome.DONE.name, Outcome.PARTIAL.name)
    }
}

data class LlmProfileUi(
    val apiUrl: String = "",
    val modelId: String = "",
    val bearerTokenInput: String = "",
    val hasSavedToken: Boolean = false,
    val maskedToken: String = "",
    val reasoningEffort: String = SettingsStore.DEFAULT_REASONING
)

data class LlmSettingsUi(
    val fast: LlmProfileUi = LlmProfileUi(),
    val coach: LlmProfileUi = LlmProfileUi()
)

sealed interface ConfirmResult {
    data object Stay : ConfirmResult
    data object Check : ConfirmResult
    data object Home : ConfirmResult
    data class Reloaded(val sessionId: Long) : ConfirmResult
}
