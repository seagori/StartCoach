package com.seagori.hgr.desktop

import com.seagori.hgr.sync.ClientSyncState
import com.seagori.hgr.sync.FingerprintTrustManager
import com.seagori.hgr.sync.ServerSyncState
import com.seagori.hgr.sync.SyncClient
import com.seagori.hgr.sync.SyncController
import com.seagori.hgr.sync.SyncCrypto
import com.seagori.hgr.sync.SyncHello
import com.seagori.hgr.sync.SyncHelloAck
import com.seagori.hgr.sync.SyncPairRequest
import com.seagori.hgr.sync.SyncPairResult
import com.seagori.hgr.sync.SyncServer
import com.seagori.hgr.sync.SyncTransfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncLoopbackTest {
    @Test
    fun tlsPairingAndFraming() = runBlocking {        val dir = Files.createTempDirectory("startcoach-sync-test").toFile()
        val tls = DesktopTls.loadOrCreate(dir)
        val port = ServerSocket(0).use { it.localPort }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val received = CompletableDeferred<Int>()
        val server = SyncServer(tls.sslContext, port, scope) { session ->
            val hello = session.receive() as SyncHello
            session.send(
                SyncHelloAck(
                    serverDeviceId = "server",
                    serverDeviceName = "test",
                    serverFingerprint = tls.fingerprint,
                    paired = false,
                    needsCode = true
                )
            )
            val pair = session.receive() as SyncPairRequest
            assertEquals("123456", pair.pairingCode)
            session.send(SyncPairResult(paired = true, clientToken = "token"))
            val first = session.receive()
            val push = SyncTransfer.receivePush(session, first, mutableMapOf())
            received.complete(push.second.size)
            SyncTransfer.sendChanges(session, SyncTransfer.newTransferId(), 1, push.second)
        }
        server.start()
        try {
            val trustManagers = mutableListOf<FingerprintTrustManager>()
            val clientContext = SyncCrypto.clientSslContext(tls.fingerprint, trustManagers)
            val response = withTimeout(10_000) {
                SyncClient(clientContext, "127.0.0.1", port).withSession { session ->
                    session.send(SyncHello(deviceId = "client", deviceName = "test"))
                    val ack = session.receive() as SyncHelloAck
                    assertFalse(ack.paired)
                    assertTrue(ack.needsCode)
                    session.send(SyncPairRequest("client", "test", "123456"))
                    val pair = session.receive() as SyncPairResult
                    assertTrue(pair.paired)
                    assertEquals("token", pair.clientToken)
                    SyncTransfer.sendPush(
                        session,
                        SyncTransfer.newTransferId(),
                        0,
                        "client",
                        emptyList(),
                        0
                    ) {}
                    SyncTransfer.receiveChanges(session)
                }
            }
            assertEquals(0, response.second.size)
            assertEquals(0, received.await())
            assertEquals(tls.fingerprint, trustManagers.first().observed)
        } finally {
            server.stop()
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun bidirectionalSyncConverges() = runBlocking {
        val now = 1_000_000L
        var a = DesktopData(
            goals = listOf(DesktopGoal(1, "캐릭터 그리기", createdAt = now, updatedAt = now)),
            paths = listOf(DesktopPath(1, 1, "캐릭터 경로", createdAt = now, updatedAt = now)),
            steps = listOf(
                DesktopStep(
                    id = 1,
                    pathId = 1,
                    sortOrder = 0,
                    title = "얼굴형",
                    instruction = "동그라미를 그린다",
                    criteria = "대칭인가",
                    minutes = 10,
                    difficulty = 1
                )
            ),
            sessions = listOf(
                DesktopSession(
                    id = 1,
                    goalId = 1,
                    startedAt = now,
                    endedAt = now + 1,
                    phase = "ENDED",
                    difficultyLevel = 1,
                    selectedSuggestionId = null
                )
            ),
            executions = listOf(
                DesktopExecution(
                    id = 1,
                    sessionId = 1,
                    goalId = 1,
                    suggestionId = 0,
                    actionText = "얼굴형 연습",
                    outcome = "DONE",
                    attempted = true,
                    count = 1,
                    durationSec = null,
                    feltDifficulty = 1,
                    isEstimated = false,
                    createdAt = now + 1,
                    stepId = 1
                )
            )
        )
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
        assertEquals(1, b.goals.size)
        assertEquals(1, b.paths.size)
        assertEquals(1, b.steps.size)
        assertEquals(1, b.sessions.size)
        assertEquals(1, b.executions.size)

        // B completes the step and adds a memory.
        b = b.copy(
            steps = b.steps.map { it.copy(completed = true, completedAt = now + 10) },
            memories = listOf(
                DesktopMemory(
                    goalId = b.goals.first().id,
                    summary = "첫 단계 완료",
                    updatedAt = now + 10
                )
            )
        )
        val exportB = clientController.collectLocalChanges(clientState.index, now + 10)
        serverState = serverController.flushServerLocal(serverState, now + 10)
        serverState = serverController.applyServerRemote(serverState, exportB.records, now + 10)
        val second = serverController.changesFor(serverState, clientState.lastServerVersion)
        clientState = clientController.applyClientRemote(
            clientState,
            second.records,
            second.version,
            now + 10
        )
        assertTrue(a.steps.first().completed)
        assertEquals(1, a.memories.size)
        assertEquals(1, b.memories.size)
        assertTrue(clientState.lastServerVersion >= serverState.version)

        // A deletes the goal; tombstones must remove it from B.
        a = a.copy(goals = emptyList(), paths = emptyList(), steps = emptyList(), memories = emptyList())
        serverState = serverController.flushServerLocal(serverState, now + 20)
        val third = serverController.changesFor(serverState, clientState.lastServerVersion)
        clientController.applyClientRemote(clientState, third.records, third.version, now + 20)
        assertTrue(b.goals.isEmpty())
        assertTrue(b.paths.isEmpty())
        assertTrue(b.steps.isEmpty())
        assertTrue(b.memories.isEmpty())
    }
}
