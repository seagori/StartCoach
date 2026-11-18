package com.seagori.hgr.sync

data class SyncSnapshotRecord(
    val localId: Long,
    val record: SyncRecord
)

data class SyncDiff(
    val changed: List<SyncRecord>,
    val nextIndex: List<SyncIndexEntry>,
    val deletionTimestamps: Map<String, Long> = emptyMap()
)

object SyncEngine {
    /** LWW. 시간이 같으면 deviceId가 큰 쪽이 이긴다. */
    fun isNewer(remote: SyncRecord, localUpdatedAt: Long, localDeviceId: String): Boolean {
        if (remote.updatedAt != localUpdatedAt) return remote.updatedAt > localUpdatedAt
        return remote.deviceId > localDeviceId
    }

    fun hash(record: SyncRecord): String {
        val payload = SyncCodec.json().encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            record.payload
        )
        return SyncCodec.sha256(
            listOf(record.type, record.updatedAt.toString(), record.deleted.toString(), payload)
                .joinToString("|")
        )
    }

    fun buildIndex(
        records: List<SyncSnapshotRecord>,
        versions: Map<String, Long> = emptyMap()
    ): List<SyncIndexEntry> = records.map {
        SyncIndexEntry(
            syncKey = it.record.syncKey,
            type = it.record.type,
            localId = it.localId,
            hash = hash(it.record),
            updatedAt = it.record.updatedAt,
            version = versions[it.record.syncKey] ?: 0,
            deviceId = it.record.deviceId
        )
    }

    fun diff(
        previous: List<SyncIndexEntry>,
        current: List<SyncSnapshotRecord>,
        deviceId: String,
        now: Long,
        deletionTimestamps: Map<String, Long> = emptyMap()
    ): SyncDiff {
        val previousByKey = previous.associateBy { it.syncKey }
        val currentByKey = current.associateBy { it.record.syncKey }
        val currentHashes = current.associate { it.record.syncKey to hash(it.record) }
        val changed = mutableListOf<SyncRecord>()
        val usedDeletionTimestamps = mutableMapOf<String, Long>()

        current.forEach { snapshot ->
            val old = previousByKey[snapshot.record.syncKey]
            val recordHash = currentHashes.getValue(snapshot.record.syncKey)
            if (old == null || old.hash != recordHash) {
                changed += snapshot.record
            }
        }
        previous.forEach { old ->
            if (!currentByKey.containsKey(old.syncKey)) {
                val stamp = deletionTimestamps[old.syncKey] ?: now
                usedDeletionTimestamps[old.syncKey] = stamp
                changed += SyncRecord(
                    syncKey = old.syncKey,
                    type = old.type,
                    deviceId = deviceId,
                    updatedAt = stamp,
                    deleted = true
                )
            }
        }
        val nextIndex = current.map {
            SyncIndexEntry(
                syncKey = it.record.syncKey,
                type = it.record.type,
                localId = it.localId,
                hash = currentHashes.getValue(it.record.syncKey),
                updatedAt = it.record.updatedAt,
                version = 0,
                deviceId = it.record.deviceId
            )
        }
        return SyncDiff(
            changed = changed.sortedWith(compareBy({ SyncTypes.rank(it.type) }, { it.syncKey })),
            nextIndex = nextIndex,
            deletionTimestamps = usedDeletionTimestamps
        )
    }

    /** 클라이언트가 받을 변경분: 현재 레코드 중 버전이 기준보다 큰 것 + 툼스톤. */
    fun changesSince(
        state: ServerSyncState,
        current: List<SyncSnapshotRecord>,
        baseVersion: Long
    ): List<SyncRecord> {
        if (baseVersion <= 0 || baseVersion > state.version) {
            return (current.map { it.record } + tombstonesAsRecords(state))
                .sortedBy { SyncTypes.rank(it.type) }
        }
        val indexByKey = state.index.associateBy { it.syncKey }
        val live = current.asSequence()
            .filter { (indexByKey[it.record.syncKey]?.version ?: 0) > baseVersion }
            .map { it.record }
        val deleted = state.tombstones.asSequence()
            .filter { it.version > baseVersion }
            .map { SyncRecord(it.syncKey, it.type, it.deviceId, it.updatedAt, deleted = true) }
        return (live + deleted).sortedBy { SyncTypes.rank(it.type) }.toList()
    }

    fun tombstonesAsRecords(state: ServerSyncState): List<SyncRecord> =
        state.tombstones.map { it.toRecord() }
}

fun SyncTombstone.toRecord(): SyncRecord =
    SyncRecord(syncKey = syncKey, type = type, deviceId = deviceId, updatedAt = updatedAt, deleted = true)
