package com.seagori.hgr.desktop

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.seagori.hgr.data.io.SizeLimits
import com.seagori.hgr.data.io.readLimited
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.Clarification
import com.seagori.hgr.domain.GoalDomain
import com.seagori.hgr.domain.GoalDomains
import com.seagori.hgr.domain.GoalStatus
import com.seagori.hgr.domain.GrowthStats
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.MarkdownChunker
import com.seagori.hgr.domain.Outcome
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
import com.seagori.hgr.ui.theme.AppTheme
import com.seagori.hgr.ui.theme.AppThemePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.MouseInfo
import java.awt.Point
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DesktopScreen { HOME, DAILY, RECORDS, GOALS, SETTINGS, SUGGESTION, CHECK, ARCHIVE }

class DesktopAppState(val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val store = DesktopStore()
    private val llm = DesktopLlm(settings)
    private var job: Job? = null

    private val strings get() = AppLocale.strings

    var data by mutableStateOf(store.load())
        private set

    var screen by mutableStateOf(DesktopScreen.HOME)
    var archiveReturnScreen by mutableStateOf(DesktopScreen.GOALS)
        private set
    var miniBarEnabled by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)

    var activeGoalId by mutableStateOf<Long?>(null)
        private set
    var activeSessionId by mutableStateOf<Long?>(null)
        private set
    var choices by mutableStateOf(listOf<DesktopSuggestion>())
        private set
    var selectedChoiceId by mutableStateOf<Long?>(null)
        private set
    var clarification by mutableStateOf<Clarification?>(null)
        private set
    var clarificationAnswer by mutableStateOf<String?>(null)
        private set
    var activeAction by mutableStateOf<String?>(null)
        private set
    var resultMessage by mutableStateOf<String?>(null)
        private set
    var pathCompleted by mutableStateOf(false)
        private set
    var materialExcerpt by mutableStateOf<String?>(null)
        private set

    // 목표 편집/경로
    var pathLoadingGoalId by mutableStateOf<Long?>(null)
        private set
    var pathMessage by mutableStateOf<String?>(null)
    var importMessage by mutableStateOf<String?>(null)

    // 설정
    var fastUrl by mutableStateOf(settings.apiBaseUrl(LlmProfile.FAST))
    var fastModel by mutableStateOf(settings.modelId(LlmProfile.FAST))
    var fastKey by mutableStateOf("")
    var coachUrl by mutableStateOf(settings.apiBaseUrl(LlmProfile.COACH))
    var coachModel by mutableStateOf(settings.modelId(LlmProfile.COACH))
    var coachKey by mutableStateOf("")
    var coachReasoning by mutableStateOf(settings.coachReasoningEffort)
    var language by mutableStateOf(settings.appLanguage)
    var theme by mutableStateOf(settings.appTheme)
    var reminderEnabled by mutableStateOf(settings.reminderEnabled)
    var reminderCooldownMinutes by mutableStateOf(settings.reminderCooldownMinutes)
    var reminderRetryMinutes by mutableStateOf(settings.reminderRetryMinutes)
    var reminderDurationSeconds by mutableStateOf(settings.reminderDurationSeconds)
    var reminderMessage by mutableStateOf(settings.reminderMessage)
    var dailyReminderEnabled by mutableStateOf(settings.dailyReminderEnabled)
    var dailyReminderCooldownMinutes by mutableStateOf(settings.dailyReminderCooldownMinutes)
    var dailyReminderRetryMinutes by mutableStateOf(settings.dailyReminderRetryMinutes)
    var dailyReminderDurationSeconds by mutableStateOf(settings.dailyReminderDurationSeconds)
    var activeReminderText by mutableStateOf<String?>(null)
        private set
    var testingProfile by mutableStateOf<LlmProfile?>(null)
        private set
    var testMessage by mutableStateOf<String?>(null)
    var settingsSaved by mutableStateOf(false)

    // 백업
    var backupBusy by mutableStateOf(false)
        private set
    var backupMessage by mutableStateOf<String?>(null)

    private val syncLazy = lazy {
        DesktopSyncManager(
            scope = scope,
            baseDir = File(System.getProperty("user.home"), ".startcoach"),
            deviceId = settings.syncDeviceId,
            deviceName = "${System.getProperty("user.name", "Windows")} PC",
            settings = settings,
            readData = { syncRead() },
            writeData = { transform -> syncWrite(transform) },
            onSettingsChanged = { reloadSettingsFromStore() }
        )
    }
    val sync: DesktopSyncManager get() = syncLazy.value

    val goals: List<DesktopGoal> by derivedStateOf {
        data.goals.sortedByDescending { it.updatedAt }
    }

    val visibleGoals: List<DesktopGoal> by derivedStateOf {
        goals.filter { it.status != GoalStatus.ARCHIVED.name }
    }

    val archivedGoals: List<DesktopGoal> by derivedStateOf {
        goals.filter { it.status == GoalStatus.ARCHIVED.name }
    }

    val activeGoals: List<DesktopGoal> by derivedStateOf {
        goals.filter { it.status == GoalStatus.ACTIVE.name }
    }

    val records: List<DesktopExecution> by derivedStateOf {
        data.executions.filter { !it.archived }.sortedByDescending { it.createdAt }
    }

    val archivedRecords: List<DesktopExecution> by derivedStateOf {
        data.executions.filter { it.archived }.sortedByDescending { it.createdAt }
    }

    fun openArchive(from: DesktopScreen) {
        archiveReturnScreen = from
        screen = DesktopScreen.ARCHIVE
    }

    val activeGoal: DesktopGoal?
        get() = activeGoalId?.let { id -> data.goals.firstOrNull { it.id == id } }

    val activeSession: DesktopSession?
        get() = activeSessionId?.let { id -> data.sessions.firstOrNull { it.id == id } }

    val activePath: DesktopPathUi?
        get() = activeGoalId?.let { pathUi(it) }

    val activeStep: DesktopStep?
        get() = activePath?.current

    val stepIndex: Int
        get() = activePath?.let { path -> activeStep?.let { path.steps.indexOf(it) + 1 } } ?: 0

    val stepCount: Int
        get() = activePath?.total ?: 0

    init {
        restoreOpenSession()
    }

    fun startGoal(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            error = strings.errorEnterTask
            return
        }
        val now = System.currentTimeMillis()
        val existing = data.goals.firstOrNull {
            it.title == trimmed && it.status != GoalStatus.ARCHIVED.name
        }
        val goal = if (existing == null) {
            val created = DesktopGoal(
                id = nextId(data.goals.map { it.id }),
                title = trimmed,
                domain = GoalDomains.infer(trimmed).name,
                createdAt = now,
                updatedAt = now
            )
            update { it.copy(goals = it.goals + created) }
            created
        } else {
            if (existing.status != GoalStatus.ACTIVE.name && existing.status != GoalStatus.ARCHIVED.name) {
                update {
                    it.copy(
                        goals = it.goals.map { g ->
                            if (g.id == existing.id) g.copy(status = GoalStatus.ACTIVE.name, updatedAt = now) else g
                        }
                    )
                }
            }
            existing
        }
        error = null
        startSession(goal.id, lowered = false, lastOutcome = null)
    }

    fun startSession(
        goalId: Long,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String? = null,
        skipCache: Boolean = false
    ) {
        job?.cancel()
        val now = System.currentTimeMillis()
        val open = data.sessions.firstOrNull { it.endedAt == null }
        update { d ->
            d.copy(
                sessions = d.sessions.map {
                    if (it.id == open?.id) it.copy(endedAt = now, phase = SessionPhase.ENDED.name) else it
                },
                goals = d.goals.map {
                    if (it.id == goalId) it.copy(updatedAt = now) else it
                }
            )
        }
        val cached = if (!skipCache && clarification == null && lastOutcome == null) {
            data.cachedChoices[goalId].orEmpty()
        } else {
            emptyList()
        }
        val session = DesktopSession(
            id = nextId(data.sessions.map { it.id }),
            goalId = goalId,
            startedAt = now,
            endedAt = null,
            phase = SessionStateMachine.phaseForNewSuggestions(lastOutcome).name,
            difficultyLevel = if (lowered) 0 else 1,
            selectedSuggestionId = null
        )
        update { it.copy(sessions = it.sessions + session) }
        activeGoalId = goalId
        activeSessionId = session.id
        clarificationAnswer = clarification
        activeAction = null
        resultMessage = null
        pathCompleted = false
        screen = DesktopScreen.SUGGESTION
        if (cached.isNotEmpty()) {
            val saved = persistChoices(
                session.id,
                goalId,
                cached.map {
                    com.seagori.hgr.domain.Choice(
                        text = it.text,
                        difficulty = it.difficulty,
                        kind = SuggestionKind.ACTION
                    )
                }
            )
            choices = saved
            selectedChoiceId = saved.firstOrNull()?.id
            materialExcerpt = pathUi(goalId)?.current?.let { excerptForHeading(goalId, it.sourceHeading) }
            loading = false
            return
        }
        loadChoices(
            goalId = goalId,
            sessionId = session.id,
            lowered = lowered,
            lastOutcome = lastOutcome,
            clarification = clarification,
            forceChoices = clarification != null
        )
    }

    fun easiestCachedAction(goalId: Long): String? =
        data.cachedChoices[goalId]
            ?.minByOrNull { it.difficulty }
            ?.text
            ?.takeIf { it.isNotBlank() }

    fun startQuickAction(goalId: Long, actionText: String) {
        val trimmed = actionText.trim()
        if (trimmed.isEmpty()) {
            startSession(goalId, lowered = true, lastOutcome = null)
            return
        }
        job?.cancel()
        val now = System.currentTimeMillis()
        val open = data.sessions.firstOrNull { it.endedAt == null }
        val sessionId = nextId(data.sessions.map { it.id })
        val suggestionId = nextId(data.suggestions.map { it.id })
        val suggestion = DesktopSuggestion(
            id = suggestionId,
            sessionId = sessionId,
            goalId = goalId,
            text = trimmed,
            difficulty = 0,
            kind = SuggestionKind.ACTION.name,
            selected = true,
            source = SOURCE_AI,
            createdAt = now
        )
        val session = DesktopSession(
            id = sessionId,
            goalId = goalId,
            startedAt = now,
            endedAt = null,
            phase = SessionPhase.CHECKING.name,
            difficultyLevel = 0,
            selectedSuggestionId = suggestionId
        )
        update { d ->
            d.copy(
                sessions = d.sessions.map {
                    if (it.id == open?.id) it.copy(endedAt = now, phase = SessionPhase.ENDED.name) else it
                } + session,
                goals = d.goals.map {
                    if (it.id == goalId) it.copy(updatedAt = now) else it
                },
                suggestions = d.suggestions + suggestion
            )
        }
        activeGoalId = goalId
        activeSessionId = sessionId
        choices = listOf(suggestion)
        selectedChoiceId = suggestionId
        activeAction = trimmed
        resultMessage = null
        pathCompleted = false
        loading = false
        materialExcerpt = pathUi(goalId)?.current?.let { excerptForHeading(goalId, it.sourceHeading) }
        screen = DesktopScreen.CHECK
    }

    private fun loadChoices(
        goalId: Long,
        sessionId: Long,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String?,
        forceChoices: Boolean
    ) {
        job?.cancel()
        val goal = data.goals.firstOrNull { it.id == goalId } ?: return
        val path = pathUi(goalId)
        val step = path?.current
        loading = true
        error = null
        choices = emptyList()
        selectedChoiceId = null
        this.clarification = null
        materialExcerpt = step?.let { excerptForHeading(goalId, it.sourceHeading) }
        val context = buildContext(
            goal = goal,
            step = step,
            lowered = lowered,
            lastOutcome = lastOutcome,
            clarification = clarification,
            forceChoices = forceChoices
        )
        job = scope.launch {
            val result = if (step != null) llm.fetchChoices(context, coach = true) else llm.fetchChoices(context, coach = false)
            if (activeSessionId != sessionId) return@launch
            result.onSuccess { value ->
                when (value) {
                    is SuggestionResult.Clarify -> {
                        choices = emptyList()
                        selectedChoiceId = null
                        this@DesktopAppState.clarification = Clarification(value.question, value.options)
                        clarificationAnswer = clarification
                        loading = false
                    }
                    is SuggestionResult.Choices -> {
                        if (value.choices.isEmpty()) {
                            choices = emptyList()
                            selectedChoiceId = null
                            error = strings.retryLater(strings.noSuggestions)
                            loading = false
                        } else {
                            val saved = persistChoices(sessionId, goalId, value.choices)
                            saveCachedChoices(goalId, value.choices)
                            choices = saved
                            selectedChoiceId = saved.firstOrNull()?.id
                            clarificationAnswer = clarification
                            loading = false
                        }
                    }
                }
            }.onFailure {
                error = it.toUserMessage(strings.serverConnectionFailed)
                loading = false
            }
        }
    }

    private fun buildContext(
        goal: DesktopGoal,
        step: DesktopStep?,
        lowered: Boolean,
        lastOutcome: Outcome?,
        clarification: String?,
        forceChoices: Boolean
    ): SuggestionContext {
        val memory = data.memories.firstOrNull { it.goalId == goal.id }
        val domain = runCatching { GoalDomain.valueOf(goal.domain) }.getOrDefault(GoalDomain.OTHER)
        val stepCount = pathUi(goal.id)?.total ?: 0
        return SuggestionContext(
            title = goal.title,
            domain = domain,
            lowered = lowered,
            lastProgress = memory?.lastProgress,
            lastOutcome = lastOutcome,
            curriculumExcerpt = if (step == null) progressExcerpt(goal.id) else null,
            memorySummary = memory?.summary,
            clarification = clarification,
            forceChoices = forceChoices,
            longTermTarget = goal.longTermTarget,
            stepTitle = step?.title,
            stepInstruction = step?.instruction,
            stepCriteria = step?.criteriaList.orEmpty(),
            stepPosition = step?.let { "${it.sortOrder + 1}/$stepCount" },
            stepMinutes = step?.minutes
        )
    }

    private fun persistChoices(
        sessionId: Long,
        goalId: Long,
        choices: List<com.seagori.hgr.domain.Choice>
    ): List<DesktopSuggestion> {
        val now = System.currentTimeMillis()
        val base = nextId(data.suggestions.map { it.id })
        val created = choices.mapIndexed { index, choice ->
            DesktopSuggestion(
                id = base + index,
                sessionId = sessionId,
                goalId = goalId,
                text = choice.text,
                difficulty = choice.difficulty,
                kind = choice.kind.name,
                selected = false,
                source = SOURCE_AI,
                createdAt = now
            )
        }
        update { it.copy(suggestions = it.suggestions + created) }
        return created
    }

    fun selectChoice(id: Long) {
        selectedChoiceId = id
    }

    fun otherSuggestions() {
        val goalId = activeGoalId ?: return
        val session = activeSession ?: return
        startSession(
            goalId = goalId,
            lowered = session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = clarificationAnswer,
            skipCache = true
        )
    }

    fun answerClarification(option: String) {
        val current = clarification ?: return
        val session = activeSession ?: return
        val goalId = activeGoalId ?: return
        val trimmed = option.trim()
        if (trimmed.isEmpty()) return
        val answer = "${current.question} → $trimmed"
        clarification = null
        clarificationAnswer = answer
        loadChoices(
            goalId = goalId,
            sessionId = session.id,
            lowered = session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = answer,
            forceChoices = true
        )
    }

    fun skipClarification() {
        if (clarification == null) return
        val session = activeSession ?: return
        val goalId = activeGoalId ?: return
        clarification = null
        loadChoices(
            goalId = goalId,
            sessionId = session.id,
            lowered = session.difficultyLevel == 0,
            lastOutcome = null,
            clarification = clarificationAnswer,
            forceChoices = true
        )
    }

    fun confirmSelected() {
        job?.cancel()
        loading = false
        val chosen = choices.firstOrNull { it.id == selectedChoiceId } ?: choices.firstOrNull() ?: return
        when (runCatching { SuggestionKind.valueOf(chosen.kind) }.getOrDefault(SuggestionKind.ACTION)) {
            SuggestionKind.END -> {
                stopAndHome()
            }
            SuggestionKind.LOWER, SuggestionKind.ACTION -> {
                val session = activeSession ?: return
                update {
                    it.copy(
                        suggestions = it.suggestions.map { item ->
                            if (item.id == chosen.id) item.copy(selected = true) else item
                        },
                        sessions = it.sessions.map { item ->
                            if (item.id == session.id) item.copy(
                                phase = SessionStateMachine.afterSuggestionChosen(SuggestionKind.ACTION).name,
                                selectedSuggestionId = chosen.id
                            ) else item
                        }
                    )
                }
                activeAction = chosen.text
                selectedChoiceId = chosen.id
                screen = DesktopScreen.CHECK
            }
        }
    }

    fun confirmManual(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        job?.cancel()
        loading = false
        val session = activeSession ?: return
        val goalId = activeGoalId ?: return
        val now = System.currentTimeMillis()
        val suggestion = DesktopSuggestion(
            id = nextId(data.suggestions.map { it.id }),
            sessionId = session.id,
            goalId = goalId,
            text = trimmed,
            difficulty = session.difficultyLevel,
            kind = SuggestionKind.ACTION.name,
            selected = true,
            source = SOURCE_MANUAL,
            createdAt = now
        )
        update {
            it.copy(
                suggestions = it.suggestions + suggestion,
                sessions = it.sessions.map { item ->
                    if (item.id == session.id) item.copy(
                        phase = SessionStateMachine.afterSuggestionChosen(SuggestionKind.ACTION).name,
                        selectedSuggestionId = suggestion.id
                    ) else item
                }
            )
        }
        activeAction = trimmed
        selectedChoiceId = suggestion.id
        screen = DesktopScreen.CHECK
    }

    fun record(outcome: Outcome) {
        job?.cancel()
        loading = false
        val session = activeSession ?: return
        val goal = activeGoal ?: return
        val step = activeStep
        val now = System.currentTimeMillis()
        val attempted = outcome.isAttempted
        val action = activeAction ?: choices.firstOrNull { it.selected }?.text.orEmpty()
        val execution = DesktopExecution(
            id = nextId(data.executions.map { it.id }),
            sessionId = session.id,
            goalId = goal.id,
            suggestionId = session.selectedSuggestionId ?: 0L,
            actionText = action,
            outcome = outcome.name,
            attempted = attempted,
            count = if (attempted) 1 else 0,
            durationSec = null,
            feltDifficulty = session.difficultyLevel,
            isEstimated = false,
            createdAt = now,
            stepId = step?.id
        )
        val memory = data.memories.firstOrNull { it.goalId == goal.id }
        val newMemory = DesktopMemory(
            goalId = goal.id,
            preference = memory?.preference,
            difficultyNote = if (outcome == Outcome.NONE) strings.difficultyLowerNeeded else memory?.difficultyNote,
            lastProgress = action,
            summary = strings.memorySummary(goal.title, outcome.label()),
            updatedAt = now
        )
        val phase = SessionStateMachine.afterCheck(outcome)
        update { d ->
            var next = d.copy(
                executions = d.executions + execution,
                memories = d.memories.filter { it.goalId != goal.id } + newMemory,
                sessions = d.sessions.map {
                    if (it.id == session.id) it.copy(
                        phase = phase.name,
                        endedAt = if (phase == SessionPhase.ENDED) now else null
                    ) else it
                }
            )
            if (outcome == Outcome.DONE && step != null) {
                next = next.copy(
                    steps = next.steps.map {
                        if (it.id == step.id) it.copy(completed = true, completedAt = now) else it
                    }
                )
                next = advanceCurriculum(next, goal.id)
            }
            next
        }
        resultMessage = if (attempted) strings.outcomeAttempted(action) else strings.outcomeNotAttempted
        pathCompleted = false
        prefetchLightChoices(goal.id)
    }

    private fun advanceCurriculum(data: DesktopData, goalId: Long): DesktopData {
        val progress = data.curriculumProgress.firstOrNull { it.goalId == goalId } ?: return data
        val chunks = data.chunks.filter { it.materialId == progress.materialId }.sortedBy { it.sortOrder }
        if (chunks.isEmpty()) return data
        val nextIndex = (progress.chunkIndex + 1).coerceAtMost(chunks.lastIndex)
        if (nextIndex == progress.chunkIndex) return data
        return data.copy(
            curriculumProgress = data.curriculumProgress.map {
                if (it.goalId == goalId) it.copy(chunkIndex = nextIndex, updatedAt = System.currentTimeMillis()) else it
            }
        )
    }

    fun next() {
        val session = activeSession ?: return
        val goalId = activeGoalId ?: return
        val outcome = data.executions
            .filter { it.sessionId == session.id }
            .maxByOrNull { it.createdAt }
            ?.outcome
            ?.let { Outcome.valueOf(it) }
        if (outcome == Outcome.DONE) {
            val path = pathUi(goalId)
            if (path != null && path.total > 0 && path.current == null) {
                endSession()
                pathCompleted = true
                return
            }
        }
        startSession(
            goalId = goalId,
            lowered = SessionStateMachine.shouldLower(outcome),
            lastOutcome = outcome,
            clarification = clarificationAnswer
        )
    }

    fun goHome() {
        resultMessage = null
        pathCompleted = false
        screen = DesktopScreen.HOME
    }

    fun stopAndHome() {
        job?.cancel()
        endSession()
        resultMessage = null
        pathCompleted = false
        screen = DesktopScreen.HOME
    }

    fun backFromSuggestion() {
        job?.cancel()
        loading = false
        screen = DesktopScreen.HOME
    }

    fun backFromCheck() {
        screen = DesktopScreen.SUGGESTION
    }

    private fun endSession() {
        val session = activeSession ?: return
        val now = System.currentTimeMillis()
        update {
            it.copy(
                sessions = it.sessions.map { item ->
                    if (item.id == session.id) item.copy(endedAt = now, phase = SessionPhase.ENDED.name) else item
                }
            )
        }
        val endedGoalId = activeGoalId
        activeSessionId = null
        activeGoalId = null
        choices = emptyList()
        selectedChoiceId = null
        clarification = null
        loading = false
        materialExcerpt = null
        if (endedGoalId != null) prefetchLightChoices(endedGoalId)
    }

    private fun saveCachedChoices(goalId: Long, list: List<com.seagori.hgr.domain.Choice>) {
        val normalized = list
            .filter { it.text.isNotBlank() }
            .sortedBy { it.difficulty }
            .take(3)
            .map { DesktopCachedChoice(it.text, it.difficulty) }
        if (normalized.isEmpty()) return
        update { d -> d.copy(cachedChoices = d.cachedChoices + (goalId to normalized)) }
    }

    private fun prefetchLightChoices(goalId: Long) {
        if (!llm.isRemoteEnabled() && !llm.isCoachConfigured()) return
        val goal = data.goals.firstOrNull { it.id == goalId } ?: return
        val step = pathUi(goalId)?.current
        val context = buildContext(
            goal = goal,
            step = step,
            lowered = true,
            lastOutcome = null,
            clarification = null,
            forceChoices = true
        )
        scope.launch {
            val result = llm.fetchChoices(context, coach = step != null)
            val loaded = (result.getOrNull() as? SuggestionResult.Choices)?.choices.orEmpty()
            if (loaded.isNotEmpty()) {
                saveCachedChoices(goalId, loaded)
            }
        }
    }

    private fun restoreOpenSession() {
        val open = data.sessions.firstOrNull { it.endedAt == null } ?: return
        val goal = data.goals.firstOrNull { it.id == open.goalId } ?: return
        val route = SessionStateMachine.restoreRoute(
            runCatching { SessionPhase.valueOf(open.phase) }.getOrDefault(SessionPhase.ENDED)
        ) ?: return
        activeGoalId = goal.id
        activeSessionId = open.id
        clarificationAnswer = null
        val saved = data.suggestions.filter { it.sessionId == open.id }.sortedBy { it.id }
        choices = saved
        selectedChoiceId = open.selectedSuggestionId ?: saved.firstOrNull()?.id
        activeAction = saved.firstOrNull { it.selected }?.text
        materialExcerpt = pathUi(goal.id)?.current?.let { excerptForHeading(goal.id, it.sourceHeading) }
        screen = when (route) {
            SessionRestoreRoute.CHECK -> DesktopScreen.CHECK
            SessionRestoreRoute.SUGGESTION -> DesktopScreen.SUGGESTION
        }
        if (route == SessionRestoreRoute.SUGGESTION && saved.isEmpty()) {
            loadChoices(
                goalId = goal.id,
                sessionId = open.id,
                lowered = open.difficultyLevel == 0,
                lastOutcome = null,
                clarification = null,
                forceChoices = false
            )
        }
    }

    private fun pathUi(goalId: Long): DesktopPathUi? {
        val path = data.paths.firstOrNull { it.goalId == goalId } ?: return null
        val steps = data.steps.filter { it.pathId == path.id }.sortedBy { it.sortOrder }
        return DesktopPathUi(path, steps, steps.firstOrNull { !it.completed })
    }

    fun pathForGoal(goalId: Long): DesktopPathUi? = pathUi(goalId)

    fun materialsForGoal(goalId: Long): List<DesktopMaterial> =
        data.materials.filter { it.goalId == goalId }.sortedByDescending { it.importedAt }

    fun curriculumProgressLine(goalId: Long): String? {
        val progress = data.curriculumProgress.firstOrNull { it.goalId == goalId } ?: return null
        val material = data.materials.firstOrNull { it.id == progress.materialId } ?: return null
        val chunks = data.chunks.filter { it.materialId == material.id }.sortedBy { it.sortOrder }
        if (chunks.isEmpty()) return strings.progressBeforeStart(material.title)
        val index = progress.chunkIndex.coerceIn(0, chunks.lastIndex)
        val chunk = chunks[index]
        return "${material.title} · ${chunk.headingPath} (${index + 1}/${chunks.size})"
    }

    fun saveGoal(id: Long?, title: String, target: String?, status: GoalStatus): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return id ?: 0L
        val now = System.currentTimeMillis()
        val targetValue = target?.trim()?.ifBlank { null }
        if (id == null) {
            val created = DesktopGoal(
                id = nextId(data.goals.map { it.id }),
                title = trimmed,
                domain = GoalDomains.infer(trimmed).name,
                status = status.name,
                longTermTarget = targetValue,
                createdAt = now,
                updatedAt = now
            )
            update { it.copy(goals = it.goals + created) }
            return created.id
        }
        update {
            it.copy(
                goals = it.goals.map { goal ->
                    if (goal.id == id) goal.copy(
                        title = trimmed,
                        status = status.name,
                        longTermTarget = targetValue,
                        updatedAt = now
                    ) else goal
                }
            )
        }
        return id
    }

    fun deleteGoal(goal: DesktopGoal) {
        if (activeGoalId == goal.id) {
            job?.cancel()
            activeGoalId = null
            activeSessionId = null
            choices = emptyList()
            screen = DesktopScreen.HOME
        }
        update { d ->
            val pathIds = d.paths.filter { it.goalId == goal.id }.map { it.id }.toSet()
            val materialIds = d.materials.filter { it.goalId == goal.id }.map { it.id }.toSet()
            d.copy(
                goals = d.goals.filterNot { it.id == goal.id },
                sessions = d.sessions.filterNot { it.goalId == goal.id },
                suggestions = d.suggestions.filterNot { it.goalId == goal.id },
                executions = d.executions.filterNot { it.goalId == goal.id },
                memories = d.memories.filterNot { it.goalId == goal.id },
                materials = d.materials.filterNot { it.goalId == goal.id },
                chunks = d.chunks.filterNot { it.materialId in materialIds },
                curriculumProgress = d.curriculumProgress.filterNot { it.goalId == goal.id },
                paths = d.paths.filterNot { it.goalId == goal.id },
                steps = d.steps.filterNot { it.pathId in pathIds }
            )
        }
    }

    fun buildPath(goalId: Long, title: String, target: String?, status: GoalStatus) {
        if (pathLoadingGoalId != null) return
        saveGoal(goalId, title, target, status)
        pathLoadingGoalId = goalId
        pathMessage = null
        val goal = data.goals.firstOrNull { it.id == goalId } ?: run {
            pathLoadingGoalId = null
            return
        }
        val outline = outlineForGoal(goalId)
        scope.launch {
            val result = runCatching {
                llm.designPath(
                    PathPlanRequest(
                        title = goal.title,
                        target = goal.longTermTarget,
                        materialOutline = outline?.text
                    )
                ).getOrThrow()
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
            }
            result.onSuccess { plan ->
                val now = System.currentTimeMillis()
                update { d ->
                    val old = d.paths.firstOrNull { it.goalId == goalId }
                    val pathId = nextId(d.paths.map { it.id })
                    val stepBase = nextId(d.steps.map { it.id })
                    d.copy(
                        paths = d.paths.filterNot { it.goalId == goalId } + DesktopPath(
                            id = pathId,
                            goalId = goalId,
                            title = plan.title,
                            sourceMaterialId = outline?.materialId,
                            createdAt = now,
                            updatedAt = now
                        ),
                        steps = (old?.let { existing ->
                            d.steps.filterNot { it.pathId == existing.id }
                        } ?: d.steps) + plan.steps.mapIndexed { index, step ->
                            DesktopStep(
                                id = stepBase + index,
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
                pathMessage = strings.pathBuilt
            }.onFailure {
                pathMessage = it.toUserMessage(strings.pathBuildFailed)
            }
            pathLoadingGoalId = null
        }
    }

    fun importMarkdown(goalId: Long, file: File) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val markdown = file.inputStream().use {
                        it.readLimited(SizeLimits.MARKDOWN_BYTES).decodeToString()
                    }
                    val title = file.nameWithoutExtension.ifBlank { strings.materialFallback }
                    val hash = sha256(markdown)
                    val chunks = MarkdownChunker.chunk(markdown, title)
                    ImportedMaterial(title, markdown, hash, chunks.map {
                        Triple(it.headingPath, it.body, it.order)
                    })
                }
            }
            result.onSuccess { imported ->
                val now = System.currentTimeMillis()
                val materialId = nextId(data.materials.map { it.id })
                val chunkBase = nextId(data.chunks.map { it.id })
                update { d ->
                    d.copy(
                        materials = d.materials + DesktopMaterial(
                            id = materialId,
                            goalId = goalId,
                            title = imported.title,
                            markdown = imported.markdown,
                            markdownHash = imported.hash,
                            importedAt = now
                        ),
                        chunks = d.chunks + imported.chunks.mapIndexed { index, chunk ->
                            DesktopChunk(
                                id = chunkBase + index,
                                materialId = materialId,
                                headingPath = chunk.first,
                                body = chunk.second,
                                sortOrder = chunk.third
                            )
                        },
                        curriculumProgress = d.curriculumProgress.filterNot { it.goalId == goalId } + DesktopCurriculumProgress(
                            id = nextId(d.curriculumProgress.map { it.id }),
                            goalId = goalId,
                            materialId = materialId,
                            chunkIndex = 0,
                            updatedAt = now
                        )
                    )
                }
                importMessage = strings.materialImported
                pathMessage = null
            }.onFailure {
                importMessage = it.toUserMessage(strings.importFailed)
            }
        }
    }

    fun deleteMaterial(materialId: Long, goalId: Long) {
        update { d ->
            d.copy(
                materials = d.materials.filterNot { it.id == materialId },
                chunks = d.chunks.filterNot { it.materialId == materialId },
                curriculumProgress = d.curriculumProgress.filterNot { it.goalId == goalId && it.materialId == materialId }
            )
        }
        importMessage = null
    }

    private data class ImportedMaterial(
        val title: String,
        val markdown: String,
        val hash: String,
        val chunks: List<Triple<String, String, Int>>
    )

    private fun outlineForGoal(goalId: Long, maxChars: Int = 6_000): DesktopOutline? {
        val material = materialsForGoal(goalId).firstOrNull() ?: return null
        val chunks = data.chunks.filter { it.materialId == material.id }.sortedBy { it.sortOrder }
        if (chunks.isEmpty()) return null
        val text = buildString {
            append("# ").append(material.title).append('\n')
            for (chunk in chunks) {
                if (length >= maxChars) break
                append("\n## ").append(chunk.headingPath).append('\n')
                append(chunk.body.take(500)).append('\n')
            }
        }.take(maxChars)
        return DesktopOutline(material.id, text)
    }

    private data class DesktopOutline(val materialId: Long, val text: String)

    private fun excerptForHeading(goalId: Long, heading: String?, maxChars: Int = 480): String? {
        val clean = heading?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val material = materialsForGoal(goalId).firstOrNull() ?: return null
        val chunks = data.chunks.filter { it.materialId == material.id }.sortedBy { it.sortOrder }
        val chunk = chunks.firstOrNull { it.headingPath.equals(clean, ignoreCase = true) }
            ?: chunks.firstOrNull {
                it.headingPath.contains(clean, ignoreCase = true) ||
                    clean.contains(it.headingPath, ignoreCase = true)
            }
            ?: return null
        return chunk.body.trim().take(maxChars).ifBlank { null }
    }

    private fun progressExcerpt(goalId: Long): String? {
        val progress = data.curriculumProgress.firstOrNull { it.goalId == goalId } ?: return null
        val chunks = data.chunks.filter { it.materialId == progress.materialId }.sortedBy { it.sortOrder }
        if (chunks.isEmpty()) return null
        val index = progress.chunkIndex.coerceIn(0, chunks.lastIndex)
        val current = chunks[index]
        val next = chunks.getOrNull(index + 1)
        return buildString {
            append(strings.promptCurrent).append(current.headingPath).append('\n').append(current.body.take(600))
            next?.let {
                append(strings.promptNext).append(it.headingPath).append('\n').append(it.body.take(250))
            }
        }.take(900)
    }

    fun growth(): GrowthStats {
        val history = records
        val difficulties = history.sortedBy { it.createdAt }.mapNotNull { it.feltDifficulty }
        return GrowthStats(
            firstAttempts = history.count { it.attempted },
            startSuccessRate = if (history.isEmpty()) 0f else {
                history.count { it.outcome == Outcome.DONE.name || it.outcome == Outcome.PARTIAL.name } / history.size.toFloat()
            },
            actualVolume = history.filter { !it.isEstimated }.sumOf { it.count ?: 0 },
            estimatedVolume = history.filter { it.isEstimated }.sumOf { it.count ?: 0 },
            difficultyTrend = if (difficulties.size >= 2) difficulties.last() - difficulties.first() else 0
        )
    }

    fun pathSummaries(): List<DesktopPathSummary> {
        val visibleIds = visibleGoals.map { it.id }.toSet()
        return data.paths.mapNotNull { path ->
            if (path.goalId !in visibleIds) return@mapNotNull null
            val steps = data.steps.filter { it.pathId == path.id }.sortedBy { it.sortOrder }
            if (steps.isEmpty()) return@mapNotNull null
            DesktopPathSummary(
                path = path,
                goalTitle = data.goals.firstOrNull { it.id == path.goalId }?.title ?: strings.goalFallback,
                completed = steps.count { it.completed },
                total = steps.size,
                currentStep = steps.firstOrNull { !it.completed }?.title
            )
        }
    }

    fun completedSteps(): Int = data.steps.count { it.completed }

    fun totalSteps(): Int = data.steps.size

    fun stepTitle(stepId: Long?): String? =
        stepId?.let { id -> data.steps.firstOrNull { it.id == id }?.title }

    fun goalTitle(goalId: Long): String =
        data.goals.firstOrNull { it.id == goalId }?.title ?: strings.goalFallback

    fun hasSavedKey(profile: LlmProfile): Boolean = effectiveToken(profile).isNotBlank()

    fun savedKeyHint(profile: LlmProfile): String = maskApiKey(effectiveToken(profile))

    private fun effectiveToken(profile: LlmProfile): String {
        val directUrl = settings.apiBaseUrl(profile)
        val fallbackUrl = settings.apiBaseUrl(LlmProfile.FAST)
        val (rawUrl, actualProfile) = when {
            directUrl.isNotBlank() -> directUrl to profile
            profile == LlmProfile.COACH && fallbackUrl.isNotBlank() -> fallbackUrl to LlmProfile.FAST
            else -> return ""
        }
        val baseUrl = UrlRules.normalize(rawUrl) ?: return ""
        val origin = UrlRules.origin(baseUrl) ?: return ""
        return settings.tokenFor(actualProfile, origin).orEmpty()
    }

    fun saveSettings(): Boolean {
        val fastUrl = if (fastUrlInput().isBlank()) "" else UrlRules.normalize(fastUrlInput())
        val coachUrl = if (coachUrlInput().isBlank()) "" else UrlRules.normalize(coachUrlInput())
        val tokenWithoutUrl = (fastKey.isNotBlank() && fastUrl.isNullOrBlank()) ||
            (coachKey.isNotBlank() && coachUrl.isNullOrBlank())
        if (fastUrl == null || coachUrl == null || tokenWithoutUrl) {
            testMessage = strings.invalidHttpsUrl
            return false
        }
        settings.setApiBaseUrl(LlmProfile.FAST, fastUrl)
        settings.setModelId(LlmProfile.FAST, fastModel)
        settings.setApiBaseUrl(LlmProfile.COACH, coachUrl)
        settings.setModelId(LlmProfile.COACH, coachModel)
        settings.coachReasoningEffort = coachReasoning
        if (fastKey.isNotBlank()) settings.setToken(LlmProfile.FAST, fastKey)
        if (coachKey.isNotBlank()) settings.setToken(LlmProfile.COACH, coachKey)
        // 같은 주소를 쓰면 키를 한 번만 입력해도 두 프로필이 함께 쓴다.
        if (fastUrl.isNotBlank() && fastUrl == coachUrl) {
            if (settings.token(LlmProfile.FAST).isBlank() && settings.token(LlmProfile.COACH).isNotBlank()) {
                settings.setToken(LlmProfile.FAST, settings.token(LlmProfile.COACH))
            }
            if (settings.token(LlmProfile.COACH).isBlank() && settings.token(LlmProfile.FAST).isNotBlank()) {
                settings.setToken(LlmProfile.COACH, settings.token(LlmProfile.FAST))
            }
        }
        this.fastUrl = fastUrl
        this.coachUrl = coachUrl
        fastKey = ""
        coachKey = ""
        settingsSaved = true
        testMessage = null
        return true
    }

    private fun fastUrlInput(): String = fastUrl
    private fun coachUrlInput(): String = coachUrl

    fun clearKey(profile: LlmProfile) {
        settings.setToken(profile, "")
        when (profile) {
            LlmProfile.FAST -> fastKey = ""
            LlmProfile.COACH -> coachKey = ""
        }
        testMessage = strings.keyDeleted
    }

    fun testConnection(profile: LlmProfile) {
        if (testingProfile != null) return
        if (!saveSettings()) return
        testingProfile = profile
        testMessage = null
        scope.launch {
            val result = runCatching { llm.testConnection(profile) }
                .getOrElse { Result.failure(it) }
            testMessage = result.fold(
                onSuccess = { it },
                onFailure = { it.toUserMessage(strings.connectionFailed) }
            )
            testingProfile = null
        }
    }

    fun changeLanguage(value: AppLanguage) {
        settings.appLanguage = value
        language = value
        AppLocale.set(value)
    }

    /** 동기화/백업으로 바뀐 설정을 화면 상태에 다시 읽어온다. */
    private suspend fun reloadSettingsFromStore() = withContext(Dispatchers.Main.immediate) {
        fastUrl = settings.apiBaseUrl(LlmProfile.FAST)
        fastModel = settings.modelId(LlmProfile.FAST)
        coachUrl = settings.apiBaseUrl(LlmProfile.COACH)
        coachModel = settings.modelId(LlmProfile.COACH)
        coachReasoning = settings.coachReasoningEffort
        fastKey = ""
        coachKey = ""
        testMessage = null
        val next = settings.appLanguage
        if (language != next) {
            language = next
            AppLocale.set(next)
        }
    }

    fun deleteExecution(item: DesktopExecution) {
        update { it.copy(executions = it.executions.filterNot { execution -> execution.id == item.id }) }
    }

    fun archiveExecution(item: DesktopExecution) {
        val now = System.currentTimeMillis()
        update {
            it.copy(
                executions = it.executions.map { execution ->
                    if (execution.id == item.id) execution.copy(archived = true, updatedAt = now) else execution
                }
            )
        }
    }

    fun unarchiveExecution(item: DesktopExecution) {
        val now = System.currentTimeMillis()
        update {
            it.copy(
                executions = it.executions.map { execution ->
                    if (execution.id == item.id) execution.copy(archived = false, updatedAt = now) else execution
                }
            )
        }
    }

    fun archiveGoal(goal: DesktopGoal) {
        val now = System.currentTimeMillis()
        job?.cancel()
        val wasActive = activeGoalId == goal.id
        update {
            it.copy(
                goals = it.goals.map { item ->
                    if (item.id == goal.id) item.copy(status = GoalStatus.ARCHIVED.name, updatedAt = now) else item
                },
                executions = it.executions.map { execution ->
                    if (execution.goalId == goal.id) execution.copy(archived = true, updatedAt = now) else execution
                },
                sessions = it.sessions.map { session ->
                    if (session.goalId == goal.id && session.endedAt == null) {
                        session.copy(endedAt = now, phase = SessionPhase.ENDED.name)
                    } else {
                        session
                    }
                }
            )
        }
        if (wasActive) {
            activeSessionId = null
            activeGoalId = null
            choices = emptyList()
            selectedChoiceId = null
            clarification = null
            loading = false
            materialExcerpt = null
            screen = DesktopScreen.HOME
        }
    }

    fun unarchiveGoal(goal: DesktopGoal) {
        val now = System.currentTimeMillis()
        update {
            it.copy(
                goals = it.goals.map { item ->
                    if (item.id == goal.id) item.copy(status = GoalStatus.ACTIVE.name, updatedAt = now) else item
                },
                executions = it.executions.map { execution ->
                    if (execution.goalId == goal.id) execution.copy(archived = false, updatedAt = now) else execution
                }
            )
        }
    }

    fun relatedRecords(goalId: Long, limit: Int = 8): List<DesktopExecution> =
        data.executions.filter { it.goalId == goalId && !it.archived }.sortedByDescending { it.createdAt }.take(limit)

    fun exportBackup(file: File, password: CharArray) {
        if (backupBusy) return
        if (password.size < MIN_BACKUP_PASSWORD) {
            backupMessage = strings.backupPasswordMin
            password.fill('\u0000')
            return
        }
        backupBusy = true
        backupMessage = null
        val snapshot = data
        val settingsSnapshot = settings.snapshot()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopBackup.export(file, snapshot, settingsSnapshot, password)
            }
            password.fill('\u0000')
            backupMessage = result.fold(
                onSuccess = { strings.backupExported },
                onFailure = { it.toUserMessage(strings.backupFailed) }
            )
            backupBusy = false
        }
    }

    fun importBackup(file: File, password: CharArray) {
        if (backupBusy) return
        backupBusy = true
        backupMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DesktopBackup.import(file, password)
            }
            password.fill('\u0000')
            result.onSuccess { pkg ->
                data = DesktopBackup.toData(pkg)
                store.save(data)
                resetSyncIndex()
                settings.applySnapshot(DesktopBackup.toSettings(pkg))
                reloadSettingsFromStore()
                job?.cancel()
                activeGoalId = null
                activeSessionId = null
                choices = emptyList()
                screen = DesktopScreen.HOME
                backupMessage = strings.backupImported
            }.onFailure {
                backupMessage = it.toUserMessage(strings.backupFailed)
            }
            backupBusy = false
        }
    }

    fun clearAllData() {
        job?.cancel()
        data = DesktopData()
        store.save(data)
        resetSyncIndex()
        activeGoalId = null
        activeSessionId = null
        choices = emptyList()
        resultMessage = null
        pathCompleted = false
        backupMessage = null
        screen = DesktopScreen.HOME
    }

    private fun resetSyncIndex() {
        if (syncLazy.isInitialized()) {
            syncLazy.value.resetSyncIndex()
        } else {
            val stateStore = DesktopSyncStateStore(File(System.getProperty("user.home"), ".startcoach"))
            val current = stateStore.load()
            stateStore.save(current.copy(version = 0L, index = emptyList(), tombstones = emptyList()))
        }
    }

    private val recordDateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

    fun recordDate(createdAt: Long): String = synchronized(recordDateFormat) {
        recordDateFormat.format(Date(createdAt))
    }

    init {
        scope.launch {
            var lastPoint: Point? = currentPointerOrNull()
            var lastActiveAt = System.currentTimeMillis()
            var nextRegularRetryAt = 0L
            var nextDailyRetryAt = 0L
            while (isActive) {
                delay(15_000L)
                if (!reminderEnabled && !dailyReminderEnabled) {
                    continue
                }
                val point = currentPointerOrNull()
                val now = System.currentTimeMillis()
                if (point != null && point != lastPoint) {
                    lastPoint = point
                    lastActiveAt = now
                }
                val userActive = (now - lastActiveAt) <= 120_000L
                val cooldownMs = reminderCooldownMinutes.coerceAtLeast(1) * 60_000L
                val lastShown = settings.lastReminderAt
                if (reminderEnabled && now >= nextRegularRetryAt && (lastShown == 0L || now - lastShown >= cooldownMs)) {
                    if (userActive) {
                        settings.lastReminderAt = now
                        nextRegularRetryAt = 0L
                        val message = reminderMessage.trim().ifEmpty { strings.reminderDefaultText }
                        activeReminderText = message
                        delay(reminderDurationSeconds.coerceAtLeast(1) * 1_000L)
                        if (activeReminderText == message) {
                            activeReminderText = null
                        }
                    } else {
                        nextRegularRetryAt = now + reminderRetryMinutes.coerceAtLeast(1) * 60_000L
                    }
                }
                val dailyCooldownMs = dailyReminderCooldownMinutes.coerceAtLeast(1) * 60_000L
                val lastDaily = settings.lastDailyReminderAt
                val hasRemaining = data.dailyTasks.tasks.isNotEmpty() && data.dailyTasks.todayPercent() < 100
                if (dailyReminderEnabled && hasRemaining && now >= nextDailyRetryAt && (lastDaily == 0L || now - lastDaily >= dailyCooldownMs)) {
                    if (userActive) {
                        settings.lastDailyReminderAt = now
                        nextDailyRetryAt = 0L
                        val message = strings.dailyReminderMessage
                        activeReminderText = message
                        delay(dailyReminderDurationSeconds.coerceAtLeast(1) * 1_000L)
                        if (activeReminderText == message) {
                            activeReminderText = null
                        }
                    } else {
                        nextDailyRetryAt = now + dailyReminderRetryMinutes.coerceAtLeast(1) * 60_000L
                    }
                }
            }
        }
    }

    private fun currentPointerOrNull(): Point? =
        runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

    fun updateReminderEnabled(enabled: Boolean) {
        reminderEnabled = enabled
        settings.reminderEnabled = enabled
        if (!enabled) {
            activeReminderText = null
        } else {
            settings.lastReminderAt = System.currentTimeMillis()
        }
    }

    fun updateReminderCooldown(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        reminderCooldownMinutes = clamped
        settings.reminderCooldownMinutes = clamped
    }

    fun updateReminderMessage(message: String) {
        reminderMessage = message
        settings.reminderMessage = message
    }

    fun updateDailyReminderEnabled(enabled: Boolean) {
        dailyReminderEnabled = enabled
        settings.dailyReminderEnabled = enabled
        if (!enabled) {
            if (activeReminderText == strings.dailyReminderMessage) {
                activeReminderText = null
            }
        } else {
            settings.lastDailyReminderAt = System.currentTimeMillis()
        }
    }

    fun updateDailyReminderCooldown(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        dailyReminderCooldownMinutes = clamped
        settings.dailyReminderCooldownMinutes = clamped
    }

    fun updateReminderRetry(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        reminderRetryMinutes = clamped
        settings.reminderRetryMinutes = clamped
    }

    fun updateReminderDuration(seconds: Int) {
        val clamped = seconds.coerceAtLeast(1)
        reminderDurationSeconds = clamped
        settings.reminderDurationSeconds = clamped
    }

    fun updateDailyReminderRetry(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        dailyReminderRetryMinutes = clamped
        settings.dailyReminderRetryMinutes = clamped
    }

    fun updateDailyReminderDuration(seconds: Int) {
        val clamped = seconds.coerceAtLeast(1)
        dailyReminderDurationSeconds = clamped
        settings.dailyReminderDurationSeconds = clamped
    }

    fun updateTheme(preset: AppThemePreset) {
        theme = preset
        settings.appTheme = preset
        AppTheme.set(preset)
    }

    fun dismissReminder() {
        activeReminderText = null
    }

    fun addDailyTask(title: String) {
        update { it.copy(dailyTasks = it.dailyTasks.addTask(title)) }
    }

    fun toggleDailyTask(taskId: Long) {
        update { it.copy(dailyTasks = it.dailyTasks.toggleTask(taskId)) }
    }

    fun deleteDailyTask(taskId: Long) {
        update { it.copy(dailyTasks = it.dailyTasks.deleteTask(taskId)) }
    }

    fun dispose() {
        if (syncLazy.isInitialized()) syncLazy.value.dispose()
        job?.cancel()
        scope.cancel()
    }

    private suspend fun syncRead(): DesktopData = withContext(Dispatchers.Main.immediate) { data }

    private suspend fun syncWrite(transform: (DesktopData) -> DesktopData): DesktopData =
        withContext(Dispatchers.Main.immediate) {
            val next = transform(data)
            data = next
            withContext(Dispatchers.IO) {
                store.save(next)
            }
            next
        }

    private fun update(transform: (DesktopData) -> DesktopData) {
        val next = transform(data)
        data = next
        scope.launch(saveDispatcher) {
            store.save(next)
        }
    }

    private fun nextId(ids: List<Long>): Long = (ids.maxOrNull() ?: 0L) + 1

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SOURCE_AI = "ai"
        const val SOURCE_MANUAL = "manual"
        const val MIN_BACKUP_PASSWORD = 8
    }
}
