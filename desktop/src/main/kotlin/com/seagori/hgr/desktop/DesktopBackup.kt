package com.seagori.hgr.desktop

import com.seagori.hgr.data.backup.PortableBackup
import com.seagori.hgr.data.backup.PortableBackupFile
import com.seagori.hgr.data.backup.PortableChunk
import com.seagori.hgr.data.backup.PortableExecution
import com.seagori.hgr.data.backup.PortableGoal
import com.seagori.hgr.data.backup.PortableMaterial
import com.seagori.hgr.data.backup.PortableMemory
import com.seagori.hgr.data.backup.PortablePath
import com.seagori.hgr.data.backup.PortableProgress
import com.seagori.hgr.data.backup.PortableSession
import com.seagori.hgr.data.backup.PortableSettings
import com.seagori.hgr.data.backup.PortableStep
import com.seagori.hgr.data.backup.PortableSuggestion
import com.seagori.hgr.data.settings.SettingsSnapshot
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.AppLocale
import java.io.File

/** 모바일과 같은 PortableBackup 형식으로 데스크탑 데이터를 백업한다. */
object DesktopBackup {
    fun export(
        file: File,
        data: DesktopData,
        settings: SettingsSnapshot,
        password: CharArray
    ): Result<Unit> = runCatching {
        try {
            writeBackup(file, data, settings, password)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun writeBackup(
        file: File,
        data: DesktopData,
        settings: SettingsSnapshot,
        password: CharArray
    ) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            AppLocale.strings.backupPasswordTooShort(MIN_PASSWORD_LENGTH)
        }
        val backup = PortableBackupFile(
            appVersion = APP_VERSION,
            exportedAt = System.currentTimeMillis(),
            settings = PortableSettings(
                fastApiUrl = settings.fastApiUrl,
                fastModelId = settings.fastModelId,
                fastToken = settings.fastToken,
                coachApiUrl = settings.coachApiUrl,
                coachModelId = settings.coachModelId,
                coachToken = settings.coachToken,
                coachReasoningEffort = settings.coachReasoningEffort,
                language = settings.language.name,
                settingsUpdatedAt = settings.updatedAt
            ),
            goals = data.goals.map {
                PortableGoal(
                    id = it.id,
                    title = it.title,
                    domain = it.domain,
                    status = it.status,
                    longTermTarget = it.longTermTarget,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            sessions = data.sessions.map {
                PortableSession(
                    id = it.id,
                    goalId = it.goalId,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    phase = it.phase,
                    difficultyLevel = it.difficultyLevel,
                    selectedSuggestionId = it.selectedSuggestionId
                )
            },
            suggestions = data.suggestions.map {
                PortableSuggestion(
                    id = it.id,
                    sessionId = it.sessionId,
                    goalId = it.goalId,
                    text = it.text,
                    difficulty = it.difficulty,
                    kind = it.kind,
                    selected = it.selected,
                    source = it.source,
                    createdAt = it.createdAt
                )
            },
            executions = data.executions.map {
                PortableExecution(
                    id = it.id,
                    sessionId = it.sessionId,
                    goalId = it.goalId,
                    suggestionId = it.suggestionId,
                    actionText = it.actionText,
                    outcome = it.outcome,
                    attempted = it.attempted,
                    count = it.count,
                    durationSec = it.durationSec,
                    feltDifficulty = it.feltDifficulty,
                    isEstimated = it.isEstimated,
                    createdAt = it.createdAt,
                    stepId = it.stepId,
                    archived = it.archived,
                    updatedAt = it.updatedAt
                )
            },
            memories = data.memories.map {
                PortableMemory(
                    goalId = it.goalId,
                    preference = it.preference,
                    difficultyNote = it.difficultyNote,
                    lastProgress = it.lastProgress,
                    summary = it.summary,
                    updatedAt = it.updatedAt
                )
            },
            materials = data.materials.map {
                PortableMaterial(
                    id = it.id,
                    goalId = it.goalId,
                    title = it.title,
                    markdown = it.markdown,
                    markdownHash = it.markdownHash,
                    importedAt = it.importedAt
                )
            },
            materialChunks = data.chunks.map {
                PortableChunk(
                    id = it.id,
                    materialId = it.materialId,
                    headingPath = it.headingPath,
                    body = it.body,
                    sortOrder = it.sortOrder
                )
            },
            curriculumProgress = data.curriculumProgress.map {
                PortableProgress(
                    id = it.id,
                    goalId = it.goalId,
                    materialId = it.materialId,
                    chunkIndex = it.chunkIndex,
                    updatedAt = it.updatedAt
                )
            },
            learningPaths = data.paths.map {
                PortablePath(
                    id = it.id,
                    goalId = it.goalId,
                    title = it.title,
                    sourceMaterialId = it.sourceMaterialId,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            learningSteps = data.steps.map {
                PortableStep(
                    id = it.id,
                    pathId = it.pathId,
                    sortOrder = it.sortOrder,
                    title = it.title,
                    instruction = it.instruction,
                    criteria = it.criteria,
                    minutes = it.minutes,
                    difficulty = it.difficulty,
                    completed = it.completed,
                    completedAt = it.completedAt,
                    sourceHeading = it.sourceHeading
                )
            }
        )
        file.outputStream().use { out -> PortableBackup.write(out, backup, password) }
    }

    fun import(file: File, password: CharArray): Result<PortableBackupFile> = runCatching {
        try {
            file.inputStream().use { input -> PortableBackup.read(input, password) }
        } finally {
            password.fill('\u0000')
        }
    }

    fun toData(backup: PortableBackupFile): DesktopData = DesktopData(
        goals = backup.goals.map {
            DesktopGoal(
                id = it.id,
                title = it.title,
                domain = it.domain,
                status = it.status,
                longTermTarget = it.longTermTarget,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        sessions = backup.sessions.map {
            DesktopSession(
                id = it.id,
                goalId = it.goalId,
                startedAt = it.startedAt,
                endedAt = it.endedAt,
                phase = it.phase,
                difficultyLevel = it.difficultyLevel,
                selectedSuggestionId = it.selectedSuggestionId
            )
        },
        suggestions = backup.suggestions.map {
            DesktopSuggestion(
                id = it.id,
                sessionId = it.sessionId,
                goalId = it.goalId,
                text = it.text,
                difficulty = it.difficulty,
                kind = it.kind,
                selected = it.selected,
                source = it.source,
                createdAt = it.createdAt
            )
        },
        executions = backup.executions.map {
            DesktopExecution(
                id = it.id,
                sessionId = it.sessionId,
                goalId = it.goalId,
                suggestionId = it.suggestionId,
                actionText = it.actionText,
                outcome = it.outcome,
                attempted = it.attempted,
                count = it.count,
                durationSec = it.durationSec,
                feltDifficulty = it.feltDifficulty,
                isEstimated = it.isEstimated,
                createdAt = it.createdAt,
                stepId = it.stepId,
                archived = it.archived,
                updatedAt = it.updatedAt
            )
        },
        memories = backup.memories.map {
            DesktopMemory(
                goalId = it.goalId,
                preference = it.preference,
                difficultyNote = it.difficultyNote,
                lastProgress = it.lastProgress,
                summary = it.summary,
                updatedAt = it.updatedAt
            )
        },
        materials = backup.materials.map {
            DesktopMaterial(
                id = it.id,
                goalId = it.goalId,
                title = it.title,
                markdown = it.markdown,
                markdownHash = it.markdownHash,
                importedAt = it.importedAt
            )
        },
        chunks = backup.materialChunks.map {
            DesktopChunk(
                id = it.id,
                materialId = it.materialId,
                headingPath = it.headingPath,
                body = it.body,
                sortOrder = it.sortOrder
            )
        },
        curriculumProgress = backup.curriculumProgress.map {
            DesktopCurriculumProgress(
                id = it.id,
                goalId = it.goalId,
                materialId = it.materialId,
                chunkIndex = it.chunkIndex,
                updatedAt = it.updatedAt
            )
        },
        paths = backup.learningPaths.map {
            DesktopPath(
                id = it.id,
                goalId = it.goalId,
                title = it.title,
                sourceMaterialId = it.sourceMaterialId,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        steps = backup.learningSteps.map {
            DesktopStep(
                id = it.id,
                pathId = it.pathId,
                sortOrder = it.sortOrder,
                title = it.title,
                instruction = it.instruction,
                criteria = it.criteria,
                minutes = it.minutes,
                difficulty = it.difficulty,
                completed = it.completed,
                completedAt = it.completedAt,
                sourceHeading = it.sourceHeading
            )
        }
    )

    fun toSettings(backup: PortableBackupFile): SettingsSnapshot = SettingsSnapshot(
        fastApiUrl = backup.settings.fastApiUrl,
        fastModelId = backup.settings.fastModelId,
        fastToken = backup.settings.fastToken,
        coachApiUrl = backup.settings.coachApiUrl,
        coachModelId = backup.settings.coachModelId,
        coachToken = backup.settings.coachToken,
        coachReasoningEffort = backup.settings.coachReasoningEffort,
        language = AppLanguage.fromStored(backup.settings.language),
        updatedAt = backup.settings.settingsUpdatedAt
    )

    private const val APP_VERSION = "0.2.0"
    private const val MIN_PASSWORD_LENGTH = 8
}
