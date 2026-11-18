package com.seagori.hgr.sync

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
import kotlinx.serialization.json.put

class AndroidSyncAdapter(
    private val db: AppDatabase,
    override val deviceId: String,
    private val settings: SettingsStore,
    private val tokens: SecureTokenStore
) : SyncStore {
    private var deviceMap: Map<String, String> = emptyMap()

    override suspend fun snapshot(index: List<SyncIndexEntry>): List<SyncSnapshotRecord> {
        deviceMap = index.associate { it.syncKey to it.deviceId }
        val goals = db.goalDao().listAll()
        val memories = db.memoryDao().listAll()
        val materials = db.materialDao().listAll()
        val chunks = db.materialChunkDao().listAll()
        val paths = db.learningPathDao().listAll()
        val steps = db.learningStepDao().listAll()
        val progress = db.curriculumProgressDao().listAll()
        val sessions = db.sessionDao().listAll()
        val suggestions = db.suggestionDao().listAll()
        val executions = db.executionDao().listAll()

        val existing = index.associateBy { it.type to it.localId }
        val goalsById = goals.associateBy { it.id }
        val materialsById = materials.associateBy { it.id }
        val pathsById = paths.associateBy { it.id }

        val goalKeys = goals.associate { it.id to key(existing, SyncTypes.GOAL, it.id, it.createdAt) }
        val materialKeys = materials.associate {
            it.id to key(existing, SyncTypes.MATERIAL, it.id, it.importedAt)
        }
        val pathKeys = paths.associate { it.id to key(existing, SyncTypes.PATH, it.id, it.createdAt) }
        val sessionKeys = sessions.associate { it.id to key(existing, SyncTypes.SESSION, it.id, it.startedAt) }
        val stepKeys = steps.associate { step ->
            step.id to key(
                existing,
                SyncTypes.STEP,
                step.id,
                pathsById[step.pathId]?.createdAt ?: 0L
            )
        }
        val result = mutableListOf<SyncSnapshotRecord>()

        goals.forEach { goal ->
            result += snapshot(
                goalKeys.getValue(goal.id),
                SyncTypes.GOAL,
                goal.id,
                goal.updatedAt,
                syncPayload {
                    put("title", goal.title)
                    put("domain", goal.domain)
                    put("status", goal.status)
                    putNullable("longTermTarget", goal.longTermTarget)
                    put("createdAt", goal.createdAt)
                    put("updatedAt", goal.updatedAt)
                }
            )
        }
        memories.forEach { memory ->
            val goalKey = goalKeys[memory.goalId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.MEMORY, memory.goalId, memory.updatedAt),
                SyncTypes.MEMORY,
                memory.goalId,
                memory.updatedAt,
                syncPayload {
                    put("goal", goalKey)
                    putNullable("preference", memory.preference)
                    putNullable("difficultyNote", memory.difficultyNote)
                    putNullable("lastProgress", memory.lastProgress)
                    putNullable("summary", memory.summary)
                    put("updatedAt", memory.updatedAt)
                }
            )
        }
        sessions.forEach { session ->
            val goalKey = goalKeys[session.goalId] ?: return@forEach
            result += snapshot(
                sessionKeys.getValue(session.id),
                SyncTypes.SESSION,
                session.id,
                session.endedAt ?: session.startedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("startedAt", session.startedAt)
                    putNullable("endedAt", session.endedAt)
                    put("phase", session.phase)
                    put("difficultyLevel", session.difficultyLevel)
                }
            )
        }
        suggestions.forEach { suggestion ->
            val goalKey = goalKeys[suggestion.goalId] ?: return@forEach
            val sessionKey = sessionKeys[suggestion.sessionId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.SUGGESTION, suggestion.id, suggestion.createdAt),
                SyncTypes.SUGGESTION,
                suggestion.id,
                suggestion.createdAt,
                syncPayload {
                    put("goal", goalKey)
                    put("session", sessionKey)
                    put("text", suggestion.text)
                    put("difficulty", suggestion.difficulty)
                    put("kind", suggestion.kind)
                    put("selected", suggestion.selected)
                    put("source", suggestion.source)
                    put("createdAt", suggestion.createdAt)
                }
            )
        }
        materials.forEach { material ->
            val goalKey = goalKeys[material.goalId] ?: return@forEach
            result += snapshot(
                materialKeys.getValue(material.id),
                SyncTypes.MATERIAL,
                material.id,
                material.importedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("title", material.title)
                    put("markdown", material.markdown)
                    put("markdownHash", material.markdownHash)
                    put("importedAt", material.importedAt)
                }
            )
        }
        paths.forEach { path ->
            val goalKey = goalKeys[path.goalId] ?: return@forEach
            val sourceKey = path.sourceMaterialId?.let { materialKeys[it] }
            result += snapshot(
                pathKeys.getValue(path.id),
                SyncTypes.PATH,
                path.id,
                path.updatedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("title", path.title)
                    putNullable("sourceMaterial", sourceKey)
                    put("createdAt", path.createdAt)
                    put("updatedAt", path.updatedAt)
                }
            )
        }
        chunks.forEach { chunk ->
            val material = materialsById[chunk.materialId] ?: return@forEach
            val materialKey = materialKeys[chunk.materialId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.CHUNK, chunk.id, material.importedAt),
                SyncTypes.CHUNK,
                chunk.id,
                material.importedAt,
                syncPayload {
                    put("material", materialKey)
                    put("headingPath", chunk.headingPath)
                    put("body", chunk.body)
                    put("sortOrder", chunk.sortOrder)
                }
            )
        }
        steps.forEach { step ->
            val path = pathsById[step.pathId] ?: return@forEach
            val pathKey = pathKeys[step.pathId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.STEP, step.id, path.createdAt),
                SyncTypes.STEP,
                step.id,
                maxOf(path.updatedAt, step.completedAt ?: 0L),
                syncPayload {
                    put("path", pathKey)
                    put("sortOrder", step.sortOrder)
                    put("title", step.title)
                    put("instruction", step.instruction)
                    put("criteria", step.criteria)
                    put("minutes", step.minutes)
                    put("difficulty", step.difficulty)
                    put("completed", step.completed)
                    putNullable("completedAt", step.completedAt)
                    putNullable("sourceHeading", step.sourceHeading)
                }
            )
        }
        progress.forEach { item ->
            val goalKey = goalKeys[item.goalId] ?: return@forEach
            val materialKey = materialKeys[item.materialId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.PROGRESS, item.id, item.updatedAt),
                SyncTypes.PROGRESS,
                item.id,
                item.updatedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("material", materialKey)
                    put("chunkIndex", item.chunkIndex)
                    put("updatedAt", item.updatedAt)
                }
            )
        }
        executions.forEach { execution ->
            val goalKey = goalKeys[execution.goalId] ?: return@forEach
            val sessionKey = sessionKeys[execution.sessionId] ?: return@forEach
            val stepKey = execution.stepId?.let { stepKeys[it] }
            result += snapshot(
                key(existing, SyncTypes.EXECUTION, execution.id, execution.createdAt),
                SyncTypes.EXECUTION,
                execution.id,
                execution.updatedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("session", sessionKey)
                    putNullable("step", stepKey)
                    put("actionText", execution.actionText)
                    put("outcome", execution.outcome)
                    put("attempted", execution.attempted)
                    putNullable("count", execution.count)
                    putNullable("durationSec", execution.durationSec)
                    putNullable("feltDifficulty", execution.feltDifficulty)
                    put("isEstimated", execution.isEstimated)
                    put("createdAt", execution.createdAt)
                    put("archived", execution.archived)
                    put("updatedAt", execution.updatedAt)
                }
            )
        }
        val current = settings.snapshot()
        result += snapshot(
            "settings",
            SyncTypes.SETTINGS,
            0L,
            current.updatedAt,
            syncPayload {
                put("fastApiUrl", current.fastApiUrl)
                put("fastModelId", current.fastModelId)
                put("fastToken", tokens.rawToken(LlmProfile.FAST))
                put("coachApiUrl", current.coachApiUrl)
                put("coachModelId", current.coachModelId)
                put("coachToken", tokens.rawToken(LlmProfile.COACH))
                put("coachReasoningEffort", current.coachReasoningEffort)
                put("language", current.language.name)
            }
        )
        return result
    }

    override suspend fun applyRemoteBatch(
        records: List<SyncRecord>,
        indexByKey: MutableMap<String, SyncIndexEntry>
    ): Map<String, Long?> = db.withTransaction {
        super.applyRemoteBatch(records, indexByKey)
    }

    override suspend fun applyRemote(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        // 설정은 고정 키/가상 id(0)를 쓴다. 첫 동기화에도 LWW를 적용한다.
        val existingId = localId ?: if (record.type == SyncTypes.SETTINGS) 0L else null
        if (existingId != null) {
            val localUpdatedAt = localUpdatedAt(record.type, existingId)
            val localDevice = indexByKey[record.syncKey]?.deviceId ?: deviceId
            if (!SyncEngine.isNewer(record, localUpdatedAt, localDevice)) return null
        }
        if (record.deleted) {
            val id = localId ?: return null
            delete(record.type, id)
            return id
        }
        return when (record.type) {
            SyncTypes.GOAL -> applyGoal(record, localId)
            SyncTypes.MEMORY -> applyMemory(record, indexByKey)
            SyncTypes.MATERIAL -> applyMaterial(record, localId, indexByKey)
            SyncTypes.CHUNK -> applyChunk(record, localId, indexByKey)
            SyncTypes.PROGRESS -> applyProgress(record, localId, indexByKey)
            SyncTypes.PATH -> applyPath(record, localId, indexByKey)
            SyncTypes.STEP -> applyStep(record, localId, indexByKey)
            SyncTypes.SESSION -> applySession(record, localId, indexByKey)
            SyncTypes.SUGGESTION -> applySuggestion(record, localId, indexByKey)
            SyncTypes.EXECUTION -> applyExecution(record, localId, indexByKey)
            SyncTypes.SETTINGS -> applySettings(record)
            else -> null
        }
    }

    private suspend fun applyGoal(record: SyncRecord, localId: Long?): Long? {
        val title = record.payload.string("title") ?: return null
        val existing = localId?.let { db.goalDao().getById(it) }
        val entity = GoalEntity(
            id = existing?.id ?: 0,
            title = title,
            domain = record.payload.string("domain") ?: "OTHER",
            status = record.payload.string("status") ?: "ACTIVE",
            longTermTarget = record.payload.string("longTermTarget"),
            createdAt = record.payload.long("createdAt") ?: existing?.createdAt ?: record.updatedAt,
            updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
        )
        return if (existing != null) {
            db.goalDao().update(entity)
            existing.id
        } else {
            db.goalDao().insert(entity)
        }
    }

    private suspend fun applyMemory(
        record: SyncRecord,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        val existing = db.memoryDao().getByGoal(goalId)
        if (existing != null && existing.updatedAt >= record.updatedAt) return null
        db.memoryDao().upsert(
            MemoryEntity(
                id = 0,
                goalId = goalId,
                preference = record.payload.string("preference"),
                difficultyNote = record.payload.string("difficultyNote"),
                lastProgress = record.payload.string("lastProgress"),
                summary = record.payload.string("summary"),
                updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
            )
        )
        return goalId
    }

    private suspend fun applyMaterial(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        val title = record.payload.string("title") ?: return null
        val existing = localId?.let { db.materialDao().getById(it) }
        val entity = MaterialEntity(
            id = existing?.id ?: 0,
            goalId = goalId,
            title = title,
            markdown = record.payload.string("markdown").orEmpty(),
            markdownHash = record.payload.string("markdownHash").orEmpty(),
            importedAt = record.payload.long("importedAt") ?: record.updatedAt
        )
        return if (existing != null) {
            db.materialDao().update(entity)
            existing.id
        } else {
            db.materialDao().insert(entity)
        }
    }

    private suspend fun applyChunk(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val materialId = indexByKey[record.payload.string("material")]?.localId ?: return null
        if (db.materialDao().getById(materialId) == null) return null
        val existing = localId?.let { db.materialChunkDao().getById(it) }
        val entity = MaterialChunkEntity(
            id = existing?.id ?: 0,
            materialId = materialId,
            headingPath = record.payload.string("headingPath").orEmpty(),
            body = record.payload.string("body").orEmpty(),
            sortOrder = record.payload.int("sortOrder") ?: 0
        )
        return if (existing != null) {
            db.materialChunkDao().update(entity)
            existing.id
        } else {
            db.materialChunkDao().insertAll(listOf(entity)).first()
        }
    }

    private suspend fun applyProgress(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        val materialId = indexByKey[record.payload.string("material")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        if (db.materialDao().getById(materialId) == null) return null
        val existing = db.curriculumProgressDao().getByGoal(goalId)
        if (existing != null && existing.updatedAt >= record.updatedAt) return null
        val entity = CurriculumProgressEntity(
            id = localId ?: existing?.id ?: 0,
            goalId = goalId,
            materialId = materialId,
            chunkIndex = record.payload.int("chunkIndex") ?: 0,
            updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
        )
        db.curriculumProgressDao().upsert(entity)
        return entity.id.takeIf { it != 0L }
            ?: db.curriculumProgressDao().getByGoal(goalId)?.id
    }

    private suspend fun applyPath(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        val title = record.payload.string("title") ?: return null
        val source = record.payload.string("sourceMaterial")?.let { indexByKey[it]?.localId }
        val existingById = localId?.let { db.learningPathDao().getById(it) }
        val existingByGoal = db.learningPathDao().getByGoal(goalId)
        val conflict = existingByGoal?.takeIf { it.id != existingById?.id }
        if (existingById == null && conflict != null && conflict.updatedAt >= record.updatedAt) {
            return null
        }
        val entity = LearningPathEntity(
            id = existingById?.id ?: 0,
            goalId = goalId,
            title = title,
            sourceMaterialId = source,
            createdAt = record.payload.long("createdAt") ?: record.updatedAt,
            updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
        )
        return if (existingById != null) {
            db.learningPathDao().update(entity)
            existingById.id
        } else {
            conflict?.let { db.learningPathDao().delete(it) }
            db.learningPathDao().insert(entity)
        }
    }

    private suspend fun applyStep(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val pathId = indexByKey[record.payload.string("path")]?.localId ?: return null
        if (db.learningPathDao().getById(pathId) == null) return null
        val title = record.payload.string("title") ?: return null
        val existing = localId?.let { db.learningStepDao().getById(it) }
        val entity = LearningStepEntity(
            id = existing?.id ?: 0,
            pathId = pathId,
            sortOrder = record.payload.int("sortOrder") ?: 0,
            title = title,
            instruction = record.payload.string("instruction").orEmpty(),
            criteria = record.payload.string("criteria").orEmpty(),
            minutes = record.payload.int("minutes") ?: 15,
            difficulty = record.payload.int("difficulty") ?: 1,
            completed = record.payload.bool("completed") ?: false,
            completedAt = record.payload.long("completedAt"),
            sourceHeading = record.payload.string("sourceHeading")
        )
        return if (existing != null) {
            db.learningStepDao().update(entity)
            existing.id
        } else {
            db.learningStepDao().insertAll(listOf(entity)).first()
        }
    }

    private suspend fun applySession(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        val existing = localId?.let { db.sessionDao().getById(it) }
        val entity = SessionEntity(
            id = existing?.id ?: 0,
            goalId = goalId,
            startedAt = record.payload.long("startedAt") ?: record.updatedAt,
            endedAt = record.payload.long("endedAt"),
            phase = record.payload.string("phase") ?: "ENDED",
            difficultyLevel = record.payload.int("difficultyLevel") ?: 1,
            selectedSuggestionId = null
        )
        return if (existing != null) {
            db.sessionDao().update(entity)
            existing.id
        } else {
            db.sessionDao().insert(entity)
        }
    }

    private suspend fun applyExecution(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        val sessionId = indexByKey[record.payload.string("session")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        if (db.sessionDao().getById(sessionId) == null) return null
        val stepId = record.payload.string("step")?.let { indexByKey[it]?.localId }
        val existing = localId?.let { db.executionDao().getById(it) }
        val entity = ExecutionEntity(
            id = existing?.id ?: 0,
            sessionId = sessionId,
            goalId = goalId,
            suggestionId = 0,
            actionText = record.payload.string("actionText").orEmpty(),
            outcome = record.payload.string("outcome") ?: "NONE",
            attempted = record.payload.bool("attempted") ?: false,
            count = record.payload.int("count"),
            durationSec = record.payload.int("durationSec"),
            feltDifficulty = record.payload.int("feltDifficulty"),
            isEstimated = record.payload.bool("isEstimated") ?: false,
            createdAt = record.payload.long("createdAt") ?: record.updatedAt,
            stepId = stepId,
            archived = record.payload.bool("archived") ?: false,
            updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
        )
        return if (existing != null) {
            db.executionDao().update(entity)
            existing.id
        } else {
            db.executionDao().insert(entity)
        }
    }

    private suspend fun applySuggestion(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val goalId = indexByKey[record.payload.string("goal")]?.localId ?: return null
        val sessionId = indexByKey[record.payload.string("session")]?.localId ?: return null
        if (db.goalDao().getById(goalId) == null) return null
        if (db.sessionDao().getById(sessionId) == null) return null
        val text = record.payload.string("text") ?: return null
        val existing = localId?.let { db.suggestionDao().getById(it) }
        val entity = SuggestionEntity(
            id = existing?.id ?: 0,
            sessionId = sessionId,
            goalId = goalId,
            text = text,
            difficulty = record.payload.int("difficulty") ?: 1,
            kind = record.payload.string("kind").orEmpty(),
            selected = record.payload.bool("selected") ?: false,
            source = record.payload.string("source").orEmpty(),
            createdAt = record.payload.long("createdAt") ?: record.updatedAt
        )
        return if (existing != null) {
            db.suggestionDao().update(entity)
            existing.id
        } else {
            db.suggestionDao().insertAll(listOf(entity)).first()
        }
    }

    private suspend fun applySettings(record: SyncRecord): Long? {
        val payload = record.payload
        val language = AppLanguage.fromStored(payload.string("language"))
        // 동기화로 받은 URL도 사용자 입력과 동일한 검증을 거친다.
        val fastUrl = normalizeSyncedUrl(payload.string("fastApiUrl").orEmpty()) ?: return null
        val coachUrl = normalizeSyncedUrl(payload.string("coachApiUrl").orEmpty()) ?: return null
        settings.applySnapshot(
            SettingsSnapshot(
                fastApiUrl = fastUrl,
                fastModelId = payload.string("fastModelId").orEmpty(),
                coachApiUrl = coachUrl,
                coachModelId = payload.string("coachModelId").orEmpty(),
                coachReasoningEffort = payload.string("coachReasoningEffort")
                    ?: SettingsStore.DEFAULT_REASONING,
                language = language,
                updatedAt = record.updatedAt
            )
        )
        // 키는 있으면 갱신하되, 비어 있는 값으로 지우지는 않는다.
        // 기존 토큰이 그대로 전달되면 origin은 유지해 다른 URL로 재바인딩되지 않게 한다.
        applyToken(LlmProfile.FAST, payload.string("fastToken").orEmpty(), fastUrl)
        applyToken(LlmProfile.COACH, payload.string("coachToken").orEmpty(), coachUrl)
        if (AppLocale.language.value != language) AppLocale.set(language)
        return 0L
    }

    private fun normalizeSyncedUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return UrlRules.normalize(trimmed)
    }

    private fun applyToken(profile: LlmProfile, token: String, url: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return
        if (tokens.rawToken(profile) == trimmed && tokens.originFor(profile).isNotBlank()) return
        val origin = UrlRules.origin(url) ?: return
        tokens.save(profile, trimmed, origin)
    }

    private suspend fun delete(type: String, localId: Long) {
        when (type) {
            SyncTypes.GOAL -> db.goalDao().getById(localId)?.let { db.goalDao().delete(it) }
            SyncTypes.MEMORY -> db.memoryDao().deleteByGoal(localId)
            SyncTypes.MATERIAL -> {
                db.curriculumProgressDao().deleteByMaterial(localId)
                db.materialDao().deleteById(localId)
            }
            SyncTypes.CHUNK -> db.materialChunkDao().deleteById(localId)
            SyncTypes.PROGRESS -> db.curriculumProgressDao().deleteById(localId)
            SyncTypes.PATH -> db.learningPathDao().getById(localId)?.let { db.learningPathDao().delete(it) }
            SyncTypes.STEP -> db.learningStepDao().deleteById(localId)
            SyncTypes.SESSION -> db.sessionDao().deleteById(localId)
            SyncTypes.SUGGESTION -> db.suggestionDao().deleteById(localId)
            SyncTypes.EXECUTION -> db.executionDao().getById(localId)?.let { db.executionDao().delete(it) }
        }
    }

    private suspend fun localUpdatedAt(type: String, localId: Long): Long = when (type) {
        SyncTypes.GOAL -> db.goalDao().getById(localId)?.updatedAt
        SyncTypes.MEMORY -> db.memoryDao().getByGoal(localId)?.updatedAt
        SyncTypes.MATERIAL -> db.materialDao().getById(localId)?.importedAt
        SyncTypes.CHUNK -> db.materialChunkDao().getById(localId)?.let { chunk ->
            db.materialDao().getById(chunk.materialId)?.importedAt
        }
        SyncTypes.PROGRESS -> db.curriculumProgressDao().getById(localId)?.updatedAt
        SyncTypes.PATH -> db.learningPathDao().getById(localId)?.updatedAt
        SyncTypes.STEP -> db.learningStepDao().getById(localId)?.let { step ->
            val path = db.learningPathDao().getById(step.pathId)
            maxOf(path?.updatedAt ?: 0L, step.completedAt ?: 0L)
        }
        SyncTypes.SESSION -> db.sessionDao().getById(localId)?.let { it.endedAt ?: it.startedAt }
        SyncTypes.SUGGESTION -> db.suggestionDao().getById(localId)?.createdAt
        SyncTypes.EXECUTION -> db.executionDao().getById(localId)?.updatedAt
        SyncTypes.SETTINGS -> settings.settingsUpdatedAt
        else -> null
    } ?: 0L

    private fun key(
        existing: Map<Pair<String, Long>, SyncIndexEntry>,
        type: String,
        localId: Long,
        suffix: Long
    ): String = existing[type to localId]?.syncKey ?: "$deviceId:$type:$localId:$suffix"

    private fun snapshot(
        syncKey: String,
        type: String,
        localId: Long,
        updatedAt: Long,
        payload: kotlinx.serialization.json.JsonObject
    ): SyncSnapshotRecord = SyncSnapshotRecord(
        localId = localId,
        record = SyncRecord(
            syncKey = syncKey,
            type = type,
            deviceId = deviceMap[syncKey] ?: deviceId,
            updatedAt = updatedAt,
            payload = payload
        )
    )
}
