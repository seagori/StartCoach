package com.seagori.hgr.desktop

import com.seagori.hgr.sync.ResumeUnavailableException
import com.seagori.hgr.sync.SyncChunkAck
import com.seagori.hgr.sync.SyncChunkBuffer
import com.seagori.hgr.sync.SyncEngine
import com.seagori.hgr.sync.SyncError
import com.seagori.hgr.sync.SyncIndexEntry
import com.seagori.hgr.sync.SyncPushChunk
import com.seagori.hgr.sync.SyncRecord
import com.seagori.hgr.sync.SyncSession
import com.seagori.hgr.sync.SyncSnapshotRecord
import com.seagori.hgr.sync.SyncTransfer
import com.seagori.hgr.sync.SyncTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncResumeTest {
    @Test
    fun diffOrderAndDeletionStampsStayStable() {
        val previous = listOf(
            SyncIndexEntry("goal-b", SyncTypes.GOAL, 2, "old-b", 10),
            SyncIndexEntry("goal-a", SyncTypes.GOAL, 1, "old-a", 10),
            SyncIndexEntry("gone", SyncTypes.GOAL, 3, "old-gone", 4)
        )
        val current = listOf(
            snapshot("goal-b", 2, 11),
            snapshot("goal-a", 1, 11)
        )
        val reversed = current.reversed()
        val first = SyncEngine.diff(previous, current, "phone", now = 100)
        val second = SyncEngine.diff(previous, reversed, "phone", now = 100)
        assertEquals(listOf("goal-a", "goal-b", "gone"), first.changed.map { it.syncKey })
        assertEquals(
            SyncTransfer.recordsFingerprint(first.changed),
            SyncTransfer.recordsFingerprint(second.changed)
        )
        assertEquals(100, first.changed.last().updatedAt)
        val resumed = SyncEngine.diff(
            previous,
            current,
            "phone",
            now = 900,
            deletionTimestamps = first.deletionTimestamps
        )
        assertEquals(100, resumed.changed.last().updatedAt)
        assertEquals(
            SyncTransfer.recordsFingerprint(first.changed),
            SyncTransfer.recordsFingerprint(resumed.changed)
        )
    }

    @Test
    fun sendPushTreatsResumeRejectionAsRetrySignal() = runBlocking {
        val (server, client) = linkedSessions()
        try {
            val serverJob = async(Dispatchers.IO) {
                server.receive()
                server.send(SyncError(SyncTransfer.RESUME_UNAVAILABLE))
            }
            assertFailsWith<ResumeUnavailableException> {
                SyncTransfer.sendPush(client, "transfer", 0, "phone", emptyList(), 0) {}
            }
            serverJob.await()
        } finally {
            server.close()
            client.close()
        }
    }

    @Test
    fun pushResumesOnlyWhenEarlierChunksExist() = runBlocking {
        val buffers = mutableMapOf<String, SyncChunkBuffer>()
        val transferId = "transfer"
        val (server, client) = linkedSessions()
        try {
            val stalled = async(Dispatchers.IO) {
                runCatching { SyncTransfer.receivePush(server, server.receive(), buffers) }
            }
            client.send(chunk(transferId, index = 0, count = 3, key = "a"))
            assertEquals(0, (client.receive() as SyncChunkAck).index)
            client.close()
            assertTrue(stalled.await().isFailure)
        } finally {
            server.close()
        }
        assertEquals(1, buffers.size)

        val (rejectServer, rejectClient) = linkedSessions()
        try {
            val rejected = async(Dispatchers.IO) {
                runCatching { SyncTransfer.receivePush(rejectServer, rejectServer.receive(), buffers) }
            }
            rejectClient.send(chunk(transferId, index = 2, count = 3, key = "c"))
            assertEquals(
                SyncTransfer.RESUME_UNAVAILABLE,
                assertIs<SyncError>(rejectClient.receive()).message
            )
            assertTrue(rejected.await().isFailure)
        } finally {
            rejectServer.close()
            rejectClient.close()
        }

        val (resumeServer, resumeClient) = linkedSessions()
        try {
            val finished = async(Dispatchers.IO) {
                SyncTransfer.receivePush(resumeServer, resumeServer.receive(), buffers)
            }
            resumeClient.send(chunk(transferId, index = 1, count = 3, key = "b"))
            assertEquals(1, (resumeClient.receive() as SyncChunkAck).index)
            resumeClient.send(chunk(transferId, index = 2, count = 3, key = "c"))
            assertEquals(2, (resumeClient.receive() as SyncChunkAck).index)
            val records = finished.await().second
            assertEquals(listOf("a", "b", "c"), records.map { it.syncKey })
            assertTrue(buffers.isEmpty())
        } finally {
            resumeServer.close()
            resumeClient.close()
        }
    }

    private fun snapshot(key: String, localId: Long, updatedAt: Long) = SyncSnapshotRecord(
        localId = localId,
        record = SyncRecord(key, SyncTypes.GOAL, "phone", updatedAt)
    )

    private fun chunk(transferId: String, index: Int, count: Int, key: String) = SyncPushChunk(
        transferId = transferId,
        index = index,
        count = count,
        baseVersion = 4,
        deviceId = "phone",
        records = listOf(SyncRecord(key, SyncTypes.GOAL, "phone", 1))
    )

    private fun linkedSessions(): Pair<SyncSession, SyncSession> {
        val listener = ServerSocket(0)
        val client = Socket("127.0.0.1", listener.localPort)
        val server = listener.accept()
        listener.close()
        return SyncSession(server) to SyncSession(client)
    }
}
