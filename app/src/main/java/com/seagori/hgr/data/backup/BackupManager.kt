package com.seagori.hgr.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.seagori.hgr.data.local.AppDatabase
import com.seagori.hgr.data.local.entity.CurriculumProgressEntity
import com.seagori.hgr.data.local.entity.ExecutionEntity
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.data.local.entity.LearningPathEntity
import com.seagori.hgr.data.local.entity.LearningStepEntity
import com.seagori.hgr.data.local.entity.MaterialChunkEntity
import com.seagori.hgr.data.local.entity.MaterialEntity
import com.seagori.hgr.data.local.entity.MemoryEntity
import com.seagori.hgr.data.local.entity.SessionEntity
import com.seagori.hgr.data.local.entity.SuggestionEntity
import com.seagori.hgr.data.settings.SecureTokenStore
import com.seagori.hgr.data.settings.SettingsSnapshot
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 모바일 백업. 데스크탑과 같은 PortableBackup 형식을 쓰므로 서로 파일을 주고받을 수 있다.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val tokens: SecureTokenStore
) {
    suspend fun exportTo(uri: Uri, password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.size >= MIN_PASSWORD_LENGTH) {
                AppLocale.strings.backupPasswordTooShort(MIN_PASSWORD_LENGTH)
            }
            try {
                val backup = buildSnapshot()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    PortableBackup.write(out, backup, password)
                } ?: error(AppLocale.strings.cannotOpenFile)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    suspend fun importReplace(uri: Uri, password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                val backup = context.contentResolver.openInputStream(uri)?.use { input ->
                    PortableBackup.read(input, password)
                } ?: error(AppLocale.strings.cannotOpenFile)
                db.withTransaction {
                    db.goalDao().deleteAll()
                    db.goalDao().insertAll(backup.goals.map { it.toEntity() })
                    db.sessionDao().insertAll(backup.sessions.map { it.toEntity() })
                    db.suggestionDao().replaceAll(backup.suggestions.map { it.toEntity() })
                    db.executionDao().insertAll(backup.executions.map { it.toEntity() })
                    db.memoryDao().replaceAll(backup.memories.map { it.toEntity() })
                    db.materialDao().replaceAll(backup.materials.map { it.toEntity() })
                    db.materialChunkDao().replaceAll(backup.materialChunks.map { it.toEntity() })
                    db.curriculumProgressDao().replaceAll(backup.curriculumProgress.map { it.toEntity() })
                    db.learningPathDao().replaceAll(backup.learningPaths.map { it.toEntity() })
                    db.learningStepDao().replaceAll(backup.learningSteps.map { it.toEntity() })
                }
                applySettings(backup)
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private suspend fun buildSnapshot(): PortableBackupFile {
        val snapshot = settings.snapshot()
        return PortableBackupFile(
            appVersion = APP_VERSION,
            exportedAt = System.currentTimeMillis(),
            settings = PortableSettings(
                fastApiUrl = snapshot.fastApiUrl,
                fastModelId = snapshot.fastModelId,
                fastToken = tokens.rawToken(LlmProfile.FAST),
                coachApiUrl = snapshot.coachApiUrl,
                coachModelId = snapshot.coachModelId,
                coachToken = tokens.rawToken(LlmProfile.COACH),
                coachReasoningEffort = snapshot.coachReasoningEffort,
                language = snapshot.language.name,
                settingsUpdatedAt = snapshot.updatedAt
            ),
            goals = db.goalDao().listAll().map { it.toPortable() },
            sessions = db.sessionDao().listAll().map { it.toPortable() },
            suggestions = db.suggestionDao().listAll().map { it.toPortable() },
            executions = db.executionDao().listAll().map { it.toPortable() },
            memories = db.memoryDao().listAll().map { it.toPortable() },
            materials = db.materialDao().listAll().map { it.toPortable() },
            materialChunks = db.materialChunkDao().listAll().map { it.toPortable() },
            curriculumProgress = db.curriculumProgressDao().listAll().map { it.toPortable() },
            learningPaths = db.learningPathDao().listAll().map { it.toPortable() },
            learningSteps = db.learningStepDao().listAll().map { it.toPortable() }
        )
    }

    private fun applySettings(backup: PortableBackupFile) {
        val portable = backup.settings
        val language = AppLanguage.fromStored(portable.language)
        val fastUrl = normalizeBackupUrl(portable.fastApiUrl)
        val coachUrl = normalizeBackupUrl(portable.coachApiUrl)
        settings.applySnapshot(
            SettingsSnapshot(
                fastApiUrl = fastUrl,
                fastModelId = portable.fastModelId,
                coachApiUrl = coachUrl,
                coachModelId = portable.coachModelId,
                coachReasoningEffort = portable.coachReasoningEffort,
                language = language,
                updatedAt = portable.settingsUpdatedAt
            )
        )
        val fastOrigin = UrlRules.origin(fastUrl)
        if (portable.fastToken.isNotBlank() && fastOrigin != null) {
            tokens.save(LlmProfile.FAST, portable.fastToken, fastOrigin)
        } else {
            tokens.clear(LlmProfile.FAST)
        }
        val coachOrigin = UrlRules.origin(coachUrl)
        if (portable.coachToken.isNotBlank() && coachOrigin != null) {
            tokens.save(LlmProfile.COACH, portable.coachToken, coachOrigin)
        } else {
            tokens.clear(LlmProfile.COACH)
        }
        if (AppLocale.language.value != language) AppLocale.set(language)
    }

    private fun normalizeBackupUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return UrlRules.normalize(trimmed).orEmpty()
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val APP_VERSION = "0.2.0"
    }
}

private fun PortableGoal.toEntity() = GoalEntity(
    id = id,
    title = title,
    domain = domain,
    status = status,
    longTermTarget = longTermTarget,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun GoalEntity.toPortable() = PortableGoal(
    id = id,
    title = title,
    domain = domain,
    status = status,
    longTermTarget = longTermTarget,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun PortableSession.toEntity() = SessionEntity(
    id = id,
    goalId = goalId,
    startedAt = startedAt,
    endedAt = endedAt,
    phase = phase,
    difficultyLevel = difficultyLevel,
    selectedSuggestionId = selectedSuggestionId
)

private fun SessionEntity.toPortable() = PortableSession(
    id = id,
    goalId = goalId,
    startedAt = startedAt,
    endedAt = endedAt,
    phase = phase,
    difficultyLevel = difficultyLevel,
    selectedSuggestionId = selectedSuggestionId
)

private fun PortableSuggestion.toEntity() = SuggestionEntity(
    id = id,
    sessionId = sessionId,
    goalId = goalId,
    text = text,
    difficulty = difficulty,
    kind = kind,
    selected = selected,
    source = source,
    createdAt = createdAt
)

private fun SuggestionEntity.toPortable() = PortableSuggestion(
    id = id,
    sessionId = sessionId,
    goalId = goalId,
    text = text,
    difficulty = difficulty,
    kind = kind,
    selected = selected,
    source = source,
    createdAt = createdAt
)

private fun PortableExecution.toEntity() = ExecutionEntity(
    id = id,
    sessionId = sessionId,
    goalId = goalId,
    suggestionId = suggestionId,
    actionText = actionText,
    outcome = outcome,
    attempted = attempted,
    count = count,
    durationSec = durationSec,
    feltDifficulty = feltDifficulty,
    isEstimated = isEstimated,
    createdAt = createdAt,
    stepId = stepId,
    archived = archived,
    updatedAt = updatedAt
)

private fun ExecutionEntity.toPortable() = PortableExecution(
    id = id,
    sessionId = sessionId,
    goalId = goalId,
    suggestionId = suggestionId,
    actionText = actionText,
    outcome = outcome,
    attempted = attempted,
    count = count,
    durationSec = durationSec,
    feltDifficulty = feltDifficulty,
    isEstimated = isEstimated,
    createdAt = createdAt,
    stepId = stepId,
    archived = archived,
    updatedAt = updatedAt
)

private fun PortableMemory.toEntity() = MemoryEntity(
    id = id,
    goalId = goalId,
    preference = preference,
    difficultyNote = difficultyNote,
    lastProgress = lastProgress,
    summary = summary,
    updatedAt = updatedAt
)

private fun MemoryEntity.toPortable() = PortableMemory(
    id = id,
    goalId = goalId,
    preference = preference,
    difficultyNote = difficultyNote,
    lastProgress = lastProgress,
    summary = summary,
    updatedAt = updatedAt
)

private fun PortableMaterial.toEntity() = MaterialEntity(
    id = id,
    goalId = goalId,
    title = title,
    markdown = markdown,
    markdownHash = markdownHash,
    importedAt = importedAt
)

private fun MaterialEntity.toPortable() = PortableMaterial(
    id = id,
    goalId = goalId,
    title = title,
    markdown = markdown,
    markdownHash = markdownHash,
    importedAt = importedAt
)

private fun PortableChunk.toEntity() = MaterialChunkEntity(
    id = id,
    materialId = materialId,
    headingPath = headingPath,
    body = body,
    sortOrder = sortOrder
)

private fun MaterialChunkEntity.toPortable() = PortableChunk(
    id = id,
    materialId = materialId,
    headingPath = headingPath,
    body = body,
    sortOrder = sortOrder
)

private fun PortableProgress.toEntity() = CurriculumProgressEntity(
    id = id,
    goalId = goalId,
    materialId = materialId,
    chunkIndex = chunkIndex,
    updatedAt = updatedAt
)

private fun CurriculumProgressEntity.toPortable() = PortableProgress(
    id = id,
    goalId = goalId,
    materialId = materialId,
    chunkIndex = chunkIndex,
    updatedAt = updatedAt
)

private fun PortablePath.toEntity() = LearningPathEntity(
    id = id,
    goalId = goalId,
    title = title,
    sourceMaterialId = sourceMaterialId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun LearningPathEntity.toPortable() = PortablePath(
    id = id,
    goalId = goalId,
    title = title,
    sourceMaterialId = sourceMaterialId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun PortableStep.toEntity() = LearningStepEntity(
    id = id,
    pathId = pathId,
    sortOrder = sortOrder,
    title = title,
    instruction = instruction,
    criteria = criteria,
    minutes = minutes,
    difficulty = difficulty,
    completed = completed,
    completedAt = completedAt,
    sourceHeading = sourceHeading
)

private fun LearningStepEntity.toPortable() = PortableStep(
    id = id,
    pathId = pathId,
    sortOrder = sortOrder,
    title = title,
    instruction = instruction,
    criteria = criteria,
    minutes = minutes,
    difficulty = difficulty,
    completed = completed,
    completedAt = completedAt,
    sourceHeading = sourceHeading
)
