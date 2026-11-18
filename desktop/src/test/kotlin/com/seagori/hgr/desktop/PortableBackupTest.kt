package com.seagori.hgr.desktop

import com.seagori.hgr.data.settings.SettingsSnapshot
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.sync.ClientSyncState
import com.seagori.hgr.sync.PairQr
import com.seagori.hgr.sync.ServerSyncState
import com.seagori.hgr.sync.SyncController
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortableBackupTest {
    private val now = 2_000_000L

    @Test
    fun pairQrRoundTrip() {
        val text = PairQr.encode(
            host = "192.168.0.10",
            port = 45871,
            fingerprint = "AB:CD:EF:01",
            code = "123456",
            name = "데스크탑 PC"
        )
        val parsed = assertNotNull(PairQr.parse(text))
        assertEquals("192.168.0.10", parsed.host)
        assertEquals(45871, parsed.port)
        assertEquals("abcdef01", parsed.fingerprint)
        assertEquals("123456", parsed.code)
        assertEquals("데스크탑 PC", parsed.name)
    }

    @Test
    fun rejectsForeignQr() {
        assertEquals(null, PairQr.parse("https://example.com/pair?host=1.2.3.4"))
        assertEquals(null, PairQr.parse("startcoach://pair?v=2&host=1.2.3.4&code=123456"))
        assertEquals(null, PairQr.parse("startcoach://pair?v=2&host=1.2.3.4&fp=abcd&code=12"))
    }

    @Test
    fun backupRoundTripKeepsDataAndSettings() {
        val dir = Files.createTempDirectory("startcoach-backup-test").toFile()
        val file = java.io.File(dir, "backup.zip")
        val data = sampleData()
        val settings = SettingsSnapshot(
            fastApiUrl = "https://api.example.com/v1",
            fastModelId = "fast-model",
            fastToken = "fast-token",
            coachApiUrl = "https://api.example.com/v1",
            coachModelId = "coach-model",
            coachToken = "coach-token",
            coachReasoningEffort = "high",
            language = AppLanguage.JAPANESE,
            updatedAt = now
        )
        try {
            DesktopBackup.export(file, data, settings, "password123".toCharArray()).getOrThrow()
            val loaded = DesktopBackup.import(file, "password123".toCharArray()).getOrThrow()
            val restored = DesktopBackup.toData(loaded)
            assertEquals(data.goals, restored.goals)
            assertEquals(data.sessions, restored.sessions)
            assertEquals(data.suggestions, restored.suggestions)
            assertEquals(data.executions, restored.executions)
            assertEquals(data.memories, restored.memories)
            assertEquals(data.materials, restored.materials)
            assertEquals(data.chunks, restored.chunks)
            assertEquals(data.curriculumProgress, restored.curriculumProgress)
            assertEquals(data.paths, restored.paths)
            assertEquals(data.steps, restored.steps)
            val restoredSettings = DesktopBackup.toSettings(loaded)
            assertEquals(settings, restoredSettings)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun suggestionsConvergeAcrossDevices() = runBlocking {
        var a = sampleData()
        var b = DesktopData()
        val storeA = DesktopSyncStore("device-a", { a }, { transform -> a = transform(a); a })
        val storeB = DesktopSyncStore("device-b", { b }, { transform -> b = transform(b); b })
        val serverController = SyncController(storeA)
        val clientController = SyncController(storeB)

        var serverState = serverController.flushServerLocal(ServerSyncState(), now)
        val first = serverController.changesFor(serverState, 0)
        var clientState = clientController.applyClientRemote(
            ClientSyncState(),
            first.records,
            first.version,
            now
        )
        assertEquals(2, b.suggestions.size)

        // B deletes one suggestion; the tombstone must remove it on A.
        b = b.copy(suggestions = b.suggestions.drop(1))
        val exportB = clientController.collectLocalChanges(clientState.index, now + 5)
        serverState = serverController.applyServerRemote(serverState, exportB.records, now + 5)
        assertTrue(a.suggestions.size == 1)

        val second = serverController.changesFor(serverState, clientState.lastServerVersion)
        clientState = clientController.applyClientRemote(
            clientState,
            second.records,
            second.version,
            now + 5
        )
        assertEquals(1, b.suggestions.size)
    }

    @Test
    fun settingsConvergeWithKeysButBlankDoesNotErase() = runBlocking {
        val root = java.util.prefs.Preferences.userRoot().node("com/seagori/hgr/test-${java.util.UUID.randomUUID()}")
        val nodeA = root.node("a")
        val nodeB = root.node("b")
        val settingsA = com.seagori.hgr.data.settings.SettingsStore(nodeA)
        val settingsB = com.seagori.hgr.data.settings.SettingsStore(nodeB)
        settingsA.setToken(com.seagori.hgr.domain.LlmProfile.FAST, "token-a")
        settingsB.setToken(com.seagori.hgr.domain.LlmProfile.FAST, "token-b")
        var a = DesktopData()
        var b = DesktopData()
        try {
            val storeA = DesktopSyncStore("device-a", { a }, { transform -> a = transform(a); a }, settingsA)
            val storeB = DesktopSyncStore("device-b", { b }, { transform -> b = transform(b); b }, settingsB)
            val serverController = SyncController(storeA)
            val clientController = SyncController(storeB)

            var serverState = serverController.flushServerLocal(ServerSyncState(), now)
            var first = serverController.changesFor(serverState, 0)
            var clientState = clientController.applyClientRemote(
                ClientSyncState(),
                first.records,
                first.version,
                now
            )

            settingsB.setApiBaseUrl(com.seagori.hgr.domain.LlmProfile.COACH, "https://coach.example.com/v1")
            settingsB.setModelId(com.seagori.hgr.domain.LlmProfile.COACH, "coach-model-b")
            settingsB.appLanguage = AppLanguage.ENGLISH

            suspend fun syncFromB(time: Long) {
                val exportB = clientController.collectLocalChanges(clientState.index, time)
                serverState = serverController.applyServerRemote(serverState, exportB.records, time)
                val changes = serverController.changesFor(serverState, clientState.lastServerVersion)
                clientState = clientController.applyClientRemote(
                    clientState,
                    changes.records,
                    changes.version,
                    time
                )
            }

            syncFromB(now + 10)

            assertEquals("https://coach.example.com/v1", settingsA.apiBaseUrl(com.seagori.hgr.domain.LlmProfile.COACH))
            assertEquals("coach-model-b", settingsA.modelId(com.seagori.hgr.domain.LlmProfile.COACH))
            assertEquals(AppLanguage.ENGLISH, settingsA.appLanguage)
            // 최신 쪽(B)의 키가 반영된다.
            assertEquals("token-b", settingsA.token(com.seagori.hgr.domain.LlmProfile.FAST))

            // B에서 키를 지우고 다른 값을 바꿔도 A의 키는 사라지지 않는다.
            settingsB.setToken(com.seagori.hgr.domain.LlmProfile.FAST, "")
            settingsB.setModelId(com.seagori.hgr.domain.LlmProfile.COACH, "coach-model-b2")
            val export2 = clientController.collectLocalChanges(clientState.index, now + 20)
            serverState = serverController.applyServerRemote(serverState, export2.records, now + 20)
            assertEquals("coach-model-b2", settingsA.modelId(com.seagori.hgr.domain.LlmProfile.COACH))
            assertEquals("token-b", settingsA.token(com.seagori.hgr.domain.LlmProfile.FAST))
        } finally {
            runCatching { root.removeNode() }
        }
    }

    private fun sampleData() = DesktopData(
        goals = listOf(
            DesktopGoal(id = 1, title = "그리기", createdAt = now, updatedAt = now)
        ),
        sessions = listOf(
            DesktopSession(
                id = 1,
                goalId = 1,
                startedAt = now,
                endedAt = now + 1,
                phase = "ENDED",
                difficultyLevel = 2,
                selectedSuggestionId = 1
            )
        ),
        suggestions = listOf(
            DesktopSuggestion(
                id = 1,
                sessionId = 1,
                goalId = 1,
                text = "동그라미 10개",
                difficulty = 1,
                kind = "WARMUP",
                selected = true,
                source = "ai",
                createdAt = now
            ),
            DesktopSuggestion(
                id = 2,
                sessionId = 1,
                goalId = 1,
                text = "선 연습",
                difficulty = 2,
                kind = "DRILL",
                selected = false,
                source = "manual",
                createdAt = now + 1
            )
        ),
        executions = listOf(
            DesktopExecution(
                id = 1,
                sessionId = 1,
                goalId = 1,
                suggestionId = 1,
                actionText = "동그라미",
                outcome = "DONE",
                attempted = true,
                count = 10,
                durationSec = 120,
                feltDifficulty = 2,
                isEstimated = false,
                createdAt = now + 2,
                archived = false,
                updatedAt = now + 2
            )
        ),
        memories = listOf(
            DesktopMemory(goalId = 1, preference = "아침", updatedAt = now + 3)
        ),
        materials = listOf(
            DesktopMaterial(
                id = 1,
                goalId = 1,
                title = "기초",
                markdown = "# 기초",
                markdownHash = "hash",
                importedAt = now
            )
        ),
        chunks = listOf(
            DesktopChunk(id = 1, materialId = 1, headingPath = "기초", body = "본문", sortOrder = 0)
        ),
        curriculumProgress = listOf(
            DesktopCurriculumProgress(id = 1, goalId = 1, materialId = 1, chunkIndex = 0, updatedAt = now)
        ),
        paths = listOf(
            DesktopPath(id = 1, goalId = 1, title = "경로", sourceMaterialId = 1, createdAt = now, updatedAt = now)
        ),
        steps = listOf(
            DesktopStep(
                id = 1,
                pathId = 1,
                sortOrder = 0,
                title = "첫 단계",
                instruction = "그린다",
                criteria = "완성",
                minutes = 10,
                difficulty = 1
            )
        )
    )
}
