package com.seagori.hgr.sync

import com.seagori.hgr.i18n.AppLocale
import java.util.UUID

class SyncChunkBuffer(
    val transferId: String,
    val count: Int,
    val baseVersion: Long,
    val deviceId: String,
    var updatedAt: Long = System.currentTimeMillis()
) {
    private val collected = arrayOfNulls<List<SyncRecord>>(count)

    fun accept(chunk: SyncPushChunk, now: Long = System.currentTimeMillis()) {
        require(chunk.transferId == transferId) { "transfer id mismatch" }
        require(chunk.count == count) { "chunk count mismatch" }
        require(chunk.index in collected.indices) { "chunk index out of range" }
        collected[chunk.index] = chunk.records
        updatedAt = now
    }

    fun complete(): Boolean = collected.all { it != null }

    fun hasPrefix(index: Int): Boolean = (0 until index).all { collected[it] != null }

    fun records(): List<SyncRecord> = collected.flatMap { it.orEmpty() }
}

class ResumeUnavailableException : IllegalStateException(SyncTransfer.RESUME_UNAVAILABLE)

object SyncTransfer {
    const val RESUME_UNAVAILABLE = "resume unavailable"
    private const val FRAME_OVERHEAD = 1_024

    fun newTransferId(): String = UUID.randomUUID().toString()

    fun encodedSize(record: SyncRecord): Int =
        SyncCodec.json().encodeToString(SyncRecord.serializer(), record).toByteArray().size

    fun recordsFingerprint(records: List<SyncRecord>): String {
        val payload = records.joinToString("\n") { record ->
            listOf(
                record.syncKey,
                record.updatedAt.toString(),
                record.deleted.toString()
            ).joinToString("|")
        }
        return SyncCodec.sha256(payload)
    }

    fun split(records: List<SyncRecord>): List<List<SyncRecord>> {
        if (records.isEmpty()) return listOf(emptyList())
        val maxRecord = SyncProtocol.MAX_FRAME_BYTES - FRAME_OVERHEAD
        val chunks = mutableListOf<MutableList<SyncRecord>>()
        var current = mutableListOf<SyncRecord>()
        var size = 0
        records.forEach { record ->
            val bytes = encodedSize(record)
            if (bytes > maxRecord) {
                error(AppLocale.strings.syncRecordTooLarge)
            }
            if (current.isNotEmpty() && size + bytes > SyncProtocol.MAX_CHUNK_BYTES) {
                chunks += current
                current = mutableListOf()
                size = 0
            }
            current += record
            size += bytes
        }
        if (current.isNotEmpty()) chunks += current
        if (chunks.size > SyncProtocol.MAX_CHUNKS) {
            error(AppLocale.strings.syncTooManyChunks)
        }
        return chunks
    }

    fun sendPush(
        session: SyncSession,
        transferId: String,
        baseVersion: Long,
        deviceId: String,
        records: List<SyncRecord>,
        startChunk: Int,
        onChunkAcked: (Int) -> Unit
    ) {
        val chunks = split(records)
        sendPushChunks(session, transferId, baseVersion, deviceId, chunks, startChunk, onChunkAcked)
    }

    fun sendPushChunks(
        session: SyncSession,
        transferId: String,
        baseVersion: Long,
        deviceId: String,
        chunks: List<List<SyncRecord>>,
        startChunk: Int,
        onChunkAcked: (Int) -> Unit
    ) {
        val start = if (startChunk in 0 until chunks.size) startChunk else 0
        for (index in start until chunks.size) {
            session.send(
                SyncPushChunk(
                    transferId = transferId,
                    index = index,
                    count = chunks.size,
                    baseVersion = baseVersion,
                    deviceId = deviceId,
                    records = chunks[index]
                )
            )
            when (val reply = session.receive()) {
                is SyncChunkAck -> {
                    if (reply.transferId != transferId || reply.index != index) {
                        error("sync chunk ack mismatch")
                    }
                    onChunkAcked(index + 1)
                }
                is SyncError -> {
                    if (reply.message == RESUME_UNAVAILABLE) {
                        throw ResumeUnavailableException()
                    }
                    error(reply.message.ifBlank { AppLocale.strings.serverConnectionFailed })
                }
                else -> error("sync chunk ack expected")
            }
        }
    }

    fun receivePush(
        session: SyncSession,
        first: SyncMessage,
        buffers: MutableMap<String, SyncChunkBuffer>,
        now: Long = System.currentTimeMillis(),
        expireMs: Long = SyncProtocol.TRANSFER_BUFFER_MS,
        expectedDeviceId: String? = null
    ): Pair<Long, List<SyncRecord>> {
        prune(buffers, now, expireMs)
        val firstChunk = first as? SyncPushChunk ?: error("push chunk expected")
        requireValidCount(firstChunk.count)
        if (expectedDeviceId != null && firstChunk.deviceId != expectedDeviceId) {
            session.send(SyncError("device id mismatch"))
            error("device id mismatch")
        }
        val key = bufferKey(firstChunk.deviceId, firstChunk.transferId)
        val buffer = if (firstChunk.index == 0) {
            SyncChunkBuffer(
                transferId = firstChunk.transferId,
                count = firstChunk.count,
                baseVersion = firstChunk.baseVersion,
                deviceId = firstChunk.deviceId,
                updatedAt = now
            ).also { buffers[key] = it }
        } else {
            val existing = buffers[key]
            if (existing == null || existing.count != firstChunk.count ||
                existing.baseVersion != firstChunk.baseVersion
            ) {
                session.send(SyncError(RESUME_UNAVAILABLE))
                error(RESUME_UNAVAILABLE)
            }
            existing
        }
        if (firstChunk.index !in 0 until buffer.count ||
            (firstChunk.index != 0 && !buffer.hasPrefix(firstChunk.index))
        ) {
            session.send(SyncError(RESUME_UNAVAILABLE))
            error(RESUME_UNAVAILABLE)
        }
        var expected = firstChunk.index
        fun accept(chunk: SyncPushChunk) {
            if (chunk.index != expected) {
                session.send(SyncError(RESUME_UNAVAILABLE))
                error(RESUME_UNAVAILABLE)
            }
            buffer.accept(chunk, now)
            session.send(SyncChunkAck(chunk.transferId, chunk.index))
            expected += 1
        }
        accept(firstChunk)
        while (!buffer.complete()) {
            val next = session.receive() as? SyncPushChunk ?: error("push chunk expected")
            requireValidCount(next.count)
            accept(next)
        }
        buffers.remove(key)
        return buffer.baseVersion to buffer.records()
    }

    fun sendChanges(
        session: SyncSession,
        transferId: String,
        version: Long,
        records: List<SyncRecord>
    ) {
        val chunks = split(records)
        chunks.forEachIndexed { index, chunk ->
            session.send(
                SyncChangesChunk(
                    transferId = transferId,
                    index = index,
                    count = chunks.size,
                    version = version,
                    records = chunk
                )
            )
            val ack = session.receive() as? SyncChunkAck
                ?: error("sync chunk ack expected")
            if (ack.transferId != transferId || ack.index != index) {
                error("sync chunk ack mismatch")
            }
        }
    }

    fun receiveChanges(session: SyncSession): Pair<Long, List<SyncRecord>> {
        val first = session.receive() as? SyncChangesChunk
            ?: error("changes chunk expected")
        requireValidCount(first.count)
        val collected = arrayOfNulls<List<SyncRecord>>(first.count)
        fun accept(chunk: SyncChangesChunk) {
            if (chunk.transferId != first.transferId) error("transfer id mismatch")
            if (chunk.count != first.count) error("chunk count mismatch")
            if (chunk.index !in collected.indices) error("chunk index out of range")
            collected[chunk.index] = chunk.records
            session.send(SyncChunkAck(chunk.transferId, chunk.index))
        }
        accept(first)
        while (collected.any { it == null }) {
            val next = session.receive() as? SyncChangesChunk ?: error("changes chunk expected")
            requireValidCount(next.count)
            accept(next)
        }
        return first.version to collected.flatMap { it.orEmpty() }
    }

    private fun requireValidCount(count: Int) {
        if (count !in 1..SyncProtocol.MAX_CHUNKS) {
            error(AppLocale.strings.syncTooManyChunks)
        }
    }

    private fun bufferKey(deviceId: String, transferId: String) = "$deviceId|$transferId"

    private fun prune(
        buffers: MutableMap<String, SyncChunkBuffer>,
        now: Long,
        expireMs: Long
    ) {
        val expired = buffers.filter { now - it.value.updatedAt > expireMs }.keys
        expired.forEach { buffers.remove(it) }
        if (buffers.size <= SyncProtocol.MAX_TRANSFER_BUFFERS) return
        buffers.entries
            .sortedBy { it.value.updatedAt }
            .take(buffers.size - SyncProtocol.MAX_TRANSFER_BUFFERS)
            .forEach { buffers.remove(it.key) }
    }
}
