package com.seagori.hgr.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object SyncProtocol {
    const val VERSION = 2
    const val DEFAULT_PORT = 45871
    const val SERVICE_TYPE = "_startcoach._tcp.local."
    const val ANDROID_SERVICE_TYPE = "_startcoach._tcp."
    const val SERVICE_NAME = "StartCoach"
    const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    const val MAX_CHUNK_BYTES = 2 * 1024 * 1024
    const val MAX_CHUNKS = 256
    const val MAX_TRANSFER_BUFFERS = 8
    const val TRANSFER_BUFFER_MS = 30 * 60 * 1000L
}

object SyncTypes {
    const val GOAL = "goal"
    const val MEMORY = "memory"
    const val MATERIAL = "material"
    const val CHUNK = "chunk"
    const val PROGRESS = "progress"
    const val PATH = "path"
    const val STEP = "step"
    const val SESSION = "session"
    const val SUGGESTION = "suggestion"
    const val EXECUTION = "execution"
    const val SETTINGS = "settings"

    /** 부모가 자식보다 먼저 적용되도록 정렬한다. */
    val ORDER = listOf(
        GOAL,
        SESSION,
        SUGGESTION,
        MATERIAL,
        PATH,
        CHUNK,
        STEP,
        PROGRESS,
        EXECUTION,
        MEMORY,
        SETTINGS
    )

    fun rank(type: String): Int = ORDER.indexOf(type).let { if (it < 0) ORDER.size else it }
}

@Serializable
data class SyncRecord(
    val syncKey: String,
    val type: String,
    val deviceId: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val payload: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class SyncIndexEntry(
    val syncKey: String,
    val type: String,
    val localId: Long,
    val hash: String,
    val updatedAt: Long,
    val version: Long = 0,
    val deviceId: String = ""
)

@Serializable
data class SyncTombstone(
    val syncKey: String,
    val type: String,
    val version: Long,
    val deviceId: String,
    val updatedAt: Long
)

@Serializable
data class SyncPairedDevice(
    val deviceId: String,
    val name: String,
    val pairedAt: Long,
    val lastSyncAt: Long? = null,
    val token: String = ""
)

@Serializable
data class ServerSyncState(
    val version: Long = 0,
    val pairedDevices: List<SyncPairedDevice> = emptyList(),
    val index: List<SyncIndexEntry> = emptyList(),
    val tombstones: List<SyncTombstone> = emptyList()
)

@Serializable
data class ClientSyncState(
    val lastServerVersion: Long = 0,
    val serverDeviceId: String = "",
    val serverName: String = "",
    val serverFingerprint: String = "",
    val serverHost: String = "",
    val serverPort: Int = SyncProtocol.DEFAULT_PORT,
    val paired: Boolean = false,
    val clientToken: String = "",
    val index: List<SyncIndexEntry> = emptyList(),
    val lastSyncAt: Long = 0,
    val resumeTransferId: String = "",
    val resumePushIndex: Int = 0,
    val resumeBaseVersion: Long = -1,
    val resumeRecordsHash: String = "",
    val resumeDeleteStamps: Map<String, Long> = emptyMap()
)

@Serializable
sealed interface SyncMessage

@Serializable
@SerialName("hello")
data class SyncHello(
    val protocol: Int = SyncProtocol.VERSION,
    val deviceId: String,
    val deviceName: String,
    val clientToken: String = ""
) : SyncMessage

@Serializable
@SerialName("hello_ack")
data class SyncHelloAck(
    val protocol: Int = SyncProtocol.VERSION,
    val serverDeviceId: String,
    val serverDeviceName: String,
    val serverFingerprint: String,
    val paired: Boolean,
    val needsCode: Boolean
) : SyncMessage

@Serializable
@SerialName("pair_request")
data class SyncPairRequest(
    val deviceId: String,
    val deviceName: String,
    val pairingCode: String
) : SyncMessage

@Serializable
@SerialName("pair_result")
data class SyncPairResult(
    val paired: Boolean,
    val message: String = "",
    val clientToken: String = ""
) : SyncMessage

@Serializable
@SerialName("push")
data class SyncPush(
    val baseVersion: Long,
    val deviceId: String,
    val records: List<SyncRecord>
) : SyncMessage

@Serializable
@SerialName("push_chunk")
data class SyncPushChunk(
    val transferId: String,
    val index: Int,
    val count: Int,
    val baseVersion: Long,
    val deviceId: String,
    val records: List<SyncRecord>
) : SyncMessage

@Serializable
@SerialName("chunk_ack")
data class SyncChunkAck(
    val transferId: String,
    val index: Int
) : SyncMessage

@Serializable
@SerialName("changes")
data class SyncChanges(
    val version: Long,
    val records: List<SyncRecord>
) : SyncMessage

@Serializable
@SerialName("changes_chunk")
data class SyncChangesChunk(
    val transferId: String,
    val index: Int,
    val count: Int,
    val version: Long,
    val records: List<SyncRecord>
) : SyncMessage

@Serializable
@SerialName("error")
data class SyncError(
    val message: String
) : SyncMessage
