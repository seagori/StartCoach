package com.seagori.hgr.desktop

import com.seagori.hgr.data.settings.SettingsSnapshot
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.sync.SyncEngine
import com.seagori.hgr.sync.SyncIndexEntry
import com.seagori.hgr.sync.SyncRecord
import com.seagori.hgr.sync.SyncSnapshotRecord
import com.seagori.hgr.sync.SyncStore
import com.seagori.hgr.sync.SyncTypes
import com.seagori.hgr.sync.bool
import com.seagori.hgr.sync.int
import com.seagori.hgr.sync.long
import com.seagori.hgr.sync.putNullable
import com.seagori.hgr.sync.string
import com.seagori.hgr.sync.syncPayload
import kotlinx.serialization.json.put

class DesktopSyncStore(
    override val deviceId: String,
    private val readData: suspend () -> DesktopData,
    private val writeData: suspend ((DesktopData) -> DesktopData) -> DesktopData,
    private val settings: SettingsStore? = null,
    private val onSettingsChanged: suspend () -> Unit = {}
) : SyncStore {
    private var deviceMap: Map<String, String> = emptyMap()

    override suspend fun snapshot(index: List<SyncIndexEntry>): List<SyncSnapshotRecord> {
        val data = readData()
        deviceMap = index.associate { it.syncKey to it.deviceId }
        val existing = index.associateBy { it.type to it.localId }
        val goalsById = data.goals.associateBy { it.id }
        val materialsById = data.materials.associateBy { it.id }
        val pathsById = data.paths.associateBy { it.id }
        val goalKeys = data.goals.associate { it.id to key(existing, SyncTypes.GOAL, it.id, it.createdAt) }
        val materialKeys = data.materials.associate {
            it.id to key(existing, SyncTypes.MATERIAL, it.id, it.importedAt)
        }
        val pathKeys = data.paths.associate { it.id to key(existing, SyncTypes.PATH, it.id, it.createdAt) }
        val sessionKeys = data.sessions.associate {
            it.id to key(existing, SyncTypes.SESSION, it.id, it.startedAt)
        }
        val stepKeys = data.steps.associate { step ->
            step.id to key(
                existing,
                SyncTypes.STEP,
                step.id,
                pathsById[step.pathId]?.createdAt ?: 0L
            )
        }
        val result = mutableListOf<SyncSnapshotRecord>()

        data.goals.forEach { goal ->
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
        data.memories.forEach { memory ->
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
        data.materials.forEach { material ->
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
        data.sessions.forEach { session ->
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
        data.suggestions.forEach { suggestion ->
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
        data.paths.forEach { path ->
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
        data.chunks.forEach { chunk ->
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
        data.steps.forEach { step ->
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
        data.curriculumProgress.forEach { progress ->
            val goalKey = goalKeys[progress.goalId] ?: return@forEach
            val materialKey = materialKeys[progress.materialId] ?: return@forEach
            result += snapshot(
                key(existing, SyncTypes.PROGRESS, progress.id, progress.updatedAt),
                SyncTypes.PROGRESS,
                progress.id,
                progress.updatedAt,
                syncPayload {
                    put("goal", goalKey)
                    put("material", materialKey)
                    put("chunkIndex", progress.chunkIndex)
                    put("updatedAt", progress.updatedAt)
                }
            )
        }
        data.executions.forEach { execution ->
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
        val current = settings?.snapshot()
        if (current != null) {
            result += snapshot(
                "settings",
                SyncTypes.SETTINGS,
                0L,
                current.updatedAt,
                syncPayload {
                    // API 키와 엔드포인트는 기기 로컬에만 유지하고 동기화하지 않는다.
                    put("fastModelId", current.fastModelId)
                    put("coachModelId", current.coachModelId)
                    put("coachReasoningEffort", current.coachReasoningEffort)
                    put("language", current.language.name)
                }
            )
        }
        return result
    }

    override suspend fun applyRemote(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long? {
        val current = readData()
        val existingId = localId ?: if (record.type == SyncTypes.SETTINGS) 0L else null
        if (existingId != null) {
            val localUpdatedAt = localUpdatedAt(current, record.type, existingId)
            val localDevice = indexByKey[record.syncKey]?.deviceId ?: deviceId
            if (!SyncEngine.isNewer(record, localUpdatedAt, localDevice)) return null
        }
        if (record.deleted) {
            val id = localId ?: return null
            writeData { delete(it, record.type, id) }
            return id
        }
        if (record.type == SyncTypes.SETTINGS) {
            return applySettings(record)
        }
        val pair = applyRecordToData(current, record, localId, indexByKey)
        val resultId = pair.first
        if (resultId != null && pair.second !== current) {
            writeData { pair.second }
        }
        return resultId
    }

    override suspend fun applyRemoteBatch(
        records: List<SyncRecord>,
        indexByKey: MutableMap<String, SyncIndexEntry>
    ): Map<String, Long?> {
        val current = readData()
        var updatedData = current
        val results = mutableMapOf<String, Long?>()

        for (record in records) {
            val existingId = indexByKey[record.syncKey]?.localId
                ?: if (record.type == SyncTypes.SETTINGS) 0L else null
            if (existingId != null) {
                val localUpdatedAt = localUpdatedAt(updatedData, record.type, existingId)
                val localDevice = indexByKey[record.syncKey]?.deviceId ?: deviceId
                if (!SyncEngine.isNewer(record, localUpdatedAt, localDevice)) {
                    results[record.syncKey] = null
                    continue
                }
            }

            if (record.deleted) {
                val id = existingId
                if (id != null) {
                    updatedData = delete(updatedData, record.type, id)
                    results[record.syncKey] = id
                    indexByKey.remove(record.syncKey)
                } else {
                    results[record.syncKey] = null
                }
                continue
            }

            if (record.type == SyncTypes.SETTINGS) {
                val appliedId = applySettings(record)
                results[record.syncKey] = appliedId
                if (appliedId != null) {
                    indexByKey[record.syncKey] = SyncIndexEntry(
                        syncKey = record.syncKey,
                        type = record.type,
                        localId = appliedId,
                        hash = SyncEngine.hash(record),
                        updatedAt = record.updatedAt,
                        deviceId = record.deviceId
                    )
                }
                continue
            }

            val pair = applyRecordToData(updatedData, record, existingId, indexByKey)
            val newLocalId = pair.first
            updatedData = pair.second
            results[record.syncKey] = newLocalId
            if (newLocalId != null) {
                indexByKey[record.syncKey] = SyncIndexEntry(
                    syncKey = record.syncKey,
                    type = record.type,
                    localId = newLocalId,
                    hash = SyncEngine.hash(record),
                    updatedAt = record.updatedAt,
                    deviceId = record.deviceId
                )
            }
        }

        if (updatedData !== current) {
            writeData { updatedData }
        }

        return results
    }

    private fun applyRecordToData(
        data: DesktopData,
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Pair<Long?, DesktopData> = when (record.type) {
        SyncTypes.GOAL -> {
            val title = record.payload.string("title")
            if (title == null) {
                null to data
            } else if (localId != null) {
                val existing = data.goals.firstOrNull { it.id == localId }
                if (existing != null) {
                    existing.id to data.copy(
                        goals = data.goals.map {
                            if (it.id == existing.id) it.copy(
                                title = title,
                                domain = record.payload.string("domain") ?: "OTHER",
                                status = record.payload.string("status") ?: "ACTIVE",
                                longTermTarget = record.payload.string("longTermTarget"),
                                createdAt = record.payload.long("createdAt") ?: it.createdAt,
                                updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                            ) else it
                        }
                    )
                } else null to data
            } else {
                val id = nextId(data.goals.map { it.id })
                id to data.copy(
                    goals = data.goals + DesktopGoal(
                        id = id,
                        title = title,
                        domain = record.payload.string("domain") ?: "OTHER",
                        status = record.payload.string("status") ?: "ACTIVE",
                        longTermTarget = record.payload.string("longTermTarget"),
                        createdAt = record.payload.long("createdAt") ?: record.updatedAt,
                        updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                    )
                )
            }
        }
        SyncTypes.MEMORY -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            if (goalId == null || data.goals.none { it.id == goalId }) {
                null to data
            } else {
                val existing = data.memories.firstOrNull { it.goalId == goalId }
                if (existing != null && existing.updatedAt >= record.updatedAt) {
                    null to data
                } else {
                    val memory = DesktopMemory(
                        goalId = goalId,
                        preference = record.payload.string("preference"),
                        difficultyNote = record.payload.string("difficultyNote"),
                        lastProgress = record.payload.string("lastProgress"),
                        summary = record.payload.string("summary"),
                        updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                    )
                    goalId to data.copy(memories = data.memories.filterNot { it.goalId == goalId } + memory)
                }
            }
        }
        SyncTypes.MATERIAL -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            val title = record.payload.string("title")
            if (goalId == null || title == null || data.goals.none { it.id == goalId }) {
                null to data
            } else if (localId != null && data.materials.any { it.id == localId }) {
                localId to data.copy(
                    materials = data.materials.map {
                        if (it.id == localId) it.copy(
                            goalId = goalId,
                            title = title,
                            markdown = record.payload.string("markdown").orEmpty(),
                            markdownHash = record.payload.string("markdownHash").orEmpty(),
                            importedAt = record.payload.long("importedAt") ?: it.importedAt
                        ) else it
                    }
                )
            } else {
                val id = nextId(data.materials.map { it.id })
                id to data.copy(
                    materials = data.materials + DesktopMaterial(
                        id = id,
                        goalId = goalId,
                        title = title,
                        markdown = record.payload.string("markdown").orEmpty(),
                        markdownHash = record.payload.string("markdownHash").orEmpty(),
                        importedAt = record.payload.long("importedAt") ?: record.updatedAt
                    )
                )
            }
        }
        SyncTypes.CHUNK -> {
            val materialId = indexByKey[record.payload.string("material")]?.localId
            if (materialId == null || data.materials.none { it.id == materialId }) {
                null to data
            } else if (localId != null && data.chunks.any { it.id == localId }) {
                localId to data.copy(
                    chunks = data.chunks.map {
                        if (it.id == localId) it.copy(
                            materialId = materialId,
                            headingPath = record.payload.string("headingPath").orEmpty(),
                            body = record.payload.string("body").orEmpty(),
                            sortOrder = record.payload.int("sortOrder") ?: it.sortOrder
                        ) else it
                    }
                )
            } else {
                val id = nextId(data.chunks.map { it.id })
                id to data.copy(
                    chunks = data.chunks + DesktopChunk(
                        id = id,
                        materialId = materialId,
                        headingPath = record.payload.string("headingPath").orEmpty(),
                        body = record.payload.string("body").orEmpty(),
                        sortOrder = record.payload.int("sortOrder") ?: 0
                    )
                )
            }
        }
        SyncTypes.PROGRESS -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            val materialId = indexByKey[record.payload.string("material")]?.localId
            if (goalId == null || materialId == null || data.goals.none { it.id == goalId } || data.materials.none { it.id == materialId }) {
                null to data
            } else {
                val existing = data.curriculumProgress.firstOrNull { it.goalId == goalId }
                if (existing != null && existing.id != localId && existing.updatedAt >= record.updatedAt) {
                    null to data
                } else {
                    val progress = DesktopCurriculumProgress(
                        id = localId ?: existing?.id ?: nextId(data.curriculumProgress.map { it.id }),
                        goalId = goalId,
                        materialId = materialId,
                        chunkIndex = record.payload.int("chunkIndex") ?: 0,
                        updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                    )
                    progress.id to data.copy(
                        curriculumProgress = data.curriculumProgress.filterNot { it.goalId == goalId } + progress
                    )
                }
            }
        }
        SyncTypes.PATH -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            val title = record.payload.string("title")
            val source = record.payload.string("sourceMaterial")?.let { indexByKey[it]?.localId }
            if (goalId == null || title == null || data.goals.none { it.id == goalId }) {
                null to data
            } else {
                val byId = localId?.let { id -> data.paths.firstOrNull { it.id == id } }
                val conflicts = data.paths.filter { it.goalId == goalId && it.id != byId?.id }
                val newestConflict = conflicts.maxByOrNull { it.updatedAt }
                if (byId == null && newestConflict != null && newestConflict.updatedAt >= record.updatedAt) {
                    null to data
                } else {
                    val conflictIds = conflicts.map { it.id }.toSet()
                    val basePaths = data.paths.filterNot { it.id in conflictIds }
                    val baseSteps = data.steps.filterNot { it.pathId in conflictIds }
                    if (byId != null) {
                        byId.id to data.copy(
                            paths = basePaths.map {
                                if (it.id == byId.id) it.copy(
                                    goalId = goalId,
                                    title = title,
                                    sourceMaterialId = source,
                                    createdAt = record.payload.long("createdAt") ?: it.createdAt,
                                    updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                                ) else it
                            },
                            steps = baseSteps
                        )
                    } else {
                        val id = nextId(basePaths.map { it.id })
                        id to data.copy(
                            paths = basePaths + DesktopPath(
                                id = id,
                                goalId = goalId,
                                title = title,
                                sourceMaterialId = source,
                                createdAt = record.payload.long("createdAt") ?: record.updatedAt,
                                updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                            ),
                            steps = baseSteps
                        )
                    }
                }
            }
        }
        SyncTypes.STEP -> {
            val pathId = indexByKey[record.payload.string("path")]?.localId
            val title = record.payload.string("title")
            if (pathId == null || title == null || data.paths.none { it.id == pathId }) {
                null to data
            } else if (localId != null && data.steps.any { it.id == localId }) {
                localId to data.copy(
                    steps = data.steps.map {
                        if (it.id == localId) it.copy(
                            pathId = pathId,
                            sortOrder = record.payload.int("sortOrder") ?: it.sortOrder,
                            title = title,
                            instruction = record.payload.string("instruction").orEmpty(),
                            criteria = record.payload.string("criteria").orEmpty(),
                            minutes = record.payload.int("minutes") ?: it.minutes,
                            difficulty = record.payload.int("difficulty") ?: it.difficulty,
                            completed = record.payload.bool("completed") ?: it.completed,
                            completedAt = record.payload.long("completedAt"),
                            sourceHeading = record.payload.string("sourceHeading")
                        ) else it
                    }
                )
            } else {
                val id = nextId(data.steps.map { it.id })
                id to data.copy(
                    steps = data.steps + DesktopStep(
                        id = id,
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
                )
            }
        }
        SyncTypes.SESSION -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            if (goalId == null || data.goals.none { it.id == goalId }) {
                null to data
            } else {
                val existing = localId?.let { id -> data.sessions.firstOrNull { it.id == id } }
                if (existing != null) {
                    existing.id to data.copy(
                        sessions = data.sessions.map {
                            if (it.id == existing.id) it.copy(
                                goalId = goalId,
                                startedAt = record.payload.long("startedAt") ?: it.startedAt,
                                endedAt = record.payload.long("endedAt"),
                                phase = record.payload.string("phase") ?: it.phase,
                                difficultyLevel = record.payload.int("difficultyLevel") ?: it.difficultyLevel,
                                selectedSuggestionId = null
                            ) else it
                        }
                    )
                } else {
                    val id = nextId(data.sessions.map { it.id })
                    id to data.copy(
                        sessions = data.sessions + DesktopSession(
                            id = id,
                            goalId = goalId,
                            startedAt = record.payload.long("startedAt") ?: record.updatedAt,
                            endedAt = record.payload.long("endedAt"),
                            phase = record.payload.string("phase") ?: "ENDED",
                            difficultyLevel = record.payload.int("difficultyLevel") ?: 1,
                            selectedSuggestionId = null
                        )
                    )
                }
            }
        }
        SyncTypes.SUGGESTION -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            val sessionId = indexByKey[record.payload.string("session")]?.localId
            val text = record.payload.string("text")
            if (goalId == null || sessionId == null || text == null || data.goals.none { it.id == goalId } || data.sessions.none { it.id == sessionId }) {
                null to data
            } else if (localId != null && data.suggestions.any { it.id == localId }) {
                localId to data.copy(
                    suggestions = data.suggestions.map {
                        if (it.id == localId) it.copy(
                            sessionId = sessionId,
                            goalId = goalId,
                            text = text,
                            difficulty = record.payload.int("difficulty") ?: it.difficulty,
                            kind = record.payload.string("kind").orEmpty(),
                            selected = record.payload.bool("selected") ?: it.selected,
                            source = record.payload.string("source").orEmpty(),
                            createdAt = record.payload.long("createdAt") ?: it.createdAt
                        ) else it
                    }
                )
            } else {
                val id = nextId(data.suggestions.map { it.id })
                id to data.copy(
                    suggestions = data.suggestions + DesktopSuggestion(
                        id = id,
                        sessionId = sessionId,
                        goalId = goalId,
                        text = text,
                        difficulty = record.payload.int("difficulty") ?: 1,
                        kind = record.payload.string("kind").orEmpty(),
                        selected = record.payload.bool("selected") ?: false,
                        source = record.payload.string("source").orEmpty(),
                        createdAt = record.payload.long("createdAt") ?: record.updatedAt
                    )
                )
            }
        }
        SyncTypes.EXECUTION -> {
            val goalId = indexByKey[record.payload.string("goal")]?.localId
            val sessionId = indexByKey[record.payload.string("session")]?.localId
            val stepId = record.payload.string("step")?.let { indexByKey[it]?.localId }
            if (goalId == null || sessionId == null || data.goals.none { it.id == goalId } || data.sessions.none { it.id == sessionId }) {
                null to data
            } else {
                val existing = localId?.let { id -> data.executions.firstOrNull { it.id == id } }
                if (existing != null) {
                    existing.id to data.copy(
                        executions = data.executions.map {
                            if (it.id == existing.id) it.copy(
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
                                createdAt = record.payload.long("createdAt") ?: it.createdAt,
                                stepId = stepId,
                                archived = record.payload.bool("archived") ?: it.archived,
                                updatedAt = record.payload.long("updatedAt") ?: record.updatedAt
                            ) else it
                        }
                    )
                } else {
                    val id = nextId(data.executions.map { it.id })
                    id to data.copy(
                        executions = data.executions + DesktopExecution(
                            id = id,
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
                    )
                }
            }
        }
        else -> null to data
    }

    private suspend fun applySettings(record: SyncRecord): Long? {
        val store = settings ?: return null
        val payload = record.payload
        val current = store.snapshot()
        // API 키와 엔드포인트는 기기 로컬 설정을 유지한다.
        store.applySnapshot(
            SettingsSnapshot(
                fastApiUrl = current.fastApiUrl,
                fastModelId = payload.string("fastModelId").orEmpty(),
                fastToken = current.fastToken,
                coachApiUrl = current.coachApiUrl,
                coachModelId = payload.string("coachModelId").orEmpty(),
                coachToken = current.coachToken,
                coachReasoningEffort = payload.string("coachReasoningEffort")
                    ?: SettingsStore.DEFAULT_REASONING,
                language = AppLanguage.fromStored(payload.string("language")),
                updatedAt = record.updatedAt
            ),
            rebindPreservedTokens = false
        )
        onSettingsChanged()
        return 0L
    }

    private fun localUpdatedAt(data: DesktopData, type: String, localId: Long): Long = when (type) {
        SyncTypes.GOAL -> data.goals.firstOrNull { it.id == localId }?.updatedAt
        SyncTypes.MEMORY -> data.memories.firstOrNull { it.goalId == localId }?.updatedAt
        SyncTypes.MATERIAL -> data.materials.firstOrNull { it.id == localId }?.importedAt
        SyncTypes.CHUNK -> {
            val chunk = data.chunks.firstOrNull { it.id == localId }
            chunk?.let { c -> data.materials.firstOrNull { it.id == c.materialId }?.importedAt }
        }
        SyncTypes.PROGRESS -> data.curriculumProgress.firstOrNull { it.id == localId }?.updatedAt
        SyncTypes.PATH -> data.paths.firstOrNull { it.id == localId }?.updatedAt
        SyncTypes.STEP -> {
            val step = data.steps.firstOrNull { it.id == localId }
            if (step == null) null else {
                val path = data.paths.firstOrNull { it.id == step.pathId }
                maxOf(path?.updatedAt ?: 0L, step.completedAt ?: 0L)
            }
        }
        SyncTypes.SESSION -> data.sessions.firstOrNull { it.id == localId }?.let {
            it.endedAt ?: it.startedAt
        }
        SyncTypes.SUGGESTION -> data.suggestions.firstOrNull { it.id == localId }?.createdAt
        SyncTypes.EXECUTION -> data.executions.firstOrNull { it.id == localId }?.updatedAt
        SyncTypes.SETTINGS -> settings?.settingsUpdatedAt
        else -> null
    } ?: 0L

    private fun delete(data: DesktopData, type: String, localId: Long): DesktopData = when (type) {
        SyncTypes.GOAL -> {
            val pathIds = data.paths.filter { it.goalId == localId }.map { it.id }.toSet()
            val materialIds = data.materials.filter { it.goalId == localId }.map { it.id }.toSet()
            data.copy(
                goals = data.goals.filterNot { it.id == localId },
                memories = data.memories.filterNot { it.goalId == localId },
                sessions = data.sessions.filterNot { it.goalId == localId },
                executions = data.executions.filterNot { it.goalId == localId },
                paths = data.paths.filterNot { it.goalId == localId },
                steps = data.steps.filterNot { it.pathId in pathIds },
                materials = data.materials.filterNot { it.goalId == localId },
                chunks = data.chunks.filterNot { it.materialId in materialIds },
                curriculumProgress = data.curriculumProgress.filterNot { it.goalId == localId },
                suggestions = data.suggestions.filterNot { it.goalId == localId }
            )
        }
        SyncTypes.MEMORY -> data.copy(memories = data.memories.filterNot { it.goalId == localId })
        SyncTypes.MATERIAL -> data.copy(
            materials = data.materials.filterNot { it.id == localId },
            chunks = data.chunks.filterNot { it.materialId == localId },
            curriculumProgress = data.curriculumProgress.filterNot { it.materialId == localId }
        )
        SyncTypes.CHUNK -> data.copy(chunks = data.chunks.filterNot { it.id == localId })
        SyncTypes.PROGRESS -> data.copy(curriculumProgress = data.curriculumProgress.filterNot { it.id == localId })
        SyncTypes.PATH -> data.copy(
            paths = data.paths.filterNot { it.id == localId },
            steps = data.steps.filterNot { it.pathId == localId }
        )
        SyncTypes.STEP -> data.copy(steps = data.steps.filterNot { it.id == localId })
        SyncTypes.SESSION -> data.copy(
            sessions = data.sessions.filterNot { it.id == localId },
            executions = data.executions.filterNot { it.sessionId == localId },
            suggestions = data.suggestions.filterNot { it.sessionId == localId }
        )
        SyncTypes.SUGGESTION -> data.copy(suggestions = data.suggestions.filterNot { it.id == localId })
        SyncTypes.EXECUTION -> data.copy(executions = data.executions.filterNot { it.id == localId })
        else -> data
    }

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

    private fun nextId(ids: List<Long>): Long = (ids.maxOrNull() ?: 0L) + 1L
}
