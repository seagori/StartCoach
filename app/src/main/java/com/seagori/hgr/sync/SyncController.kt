package com.seagori.hgr.sync

/**
 * 플랫폼별 데이터 저장소 어댑터.
 * - snapshot: 현재 살아 있는 레코드를 syncKey와 함께 내보낸다.
 * - applyRemote: 원격 레코드를 적용하고 로컬 id를 반환한다. 거부하면 null.
 */
interface SyncStore {
    val deviceId: String

    suspend fun snapshot(index: List<SyncIndexEntry>): List<SyncSnapshotRecord>

    suspend fun applyRemote(
        record: SyncRecord,
        localId: Long?,
        indexByKey: Map<String, SyncIndexEntry>
    ): Long?

    suspend fun applyRemoteBatch(
        records: List<SyncRecord>,
        indexByKey: MutableMap<String, SyncIndexEntry>
    ): Map<String, Long?> {
        val results = mutableMapOf<String, Long?>()
        records.forEach { record ->
            val localId = applyRemote(record, indexByKey[record.syncKey]?.localId, indexByKey)
            results[record.syncKey] = localId
            if (localId != null) {
                if (record.deleted) {
                    indexByKey.remove(record.syncKey)
                } else {
                    indexByKey[record.syncKey] = SyncIndexEntry(
                        syncKey = record.syncKey,
                        type = record.type,
                        localId = localId,
                        hash = SyncEngine.hash(record),
                        updatedAt = record.updatedAt,
                        deviceId = record.deviceId
                    )
                }
            }
        }
        return results
    }
}

class SyncController(private val store: SyncStore) {
    data class LocalExport(
        val records: List<SyncRecord>,
        val pendingIndex: List<SyncIndexEntry>,
        val deletionTimestamps: Map<String, Long> = emptyMap()
    )

    suspend fun collectLocalChanges(
        index: List<SyncIndexEntry>,
        now: Long,
        deletionTimestamps: Map<String, Long> = emptyMap()
    ): LocalExport {
        val current = store.snapshot(index)
        val diff = SyncEngine.diff(index, current, store.deviceId, now, deletionTimestamps)
        return LocalExport(diff.changed, diff.nextIndex, diff.deletionTimestamps)
    }

    suspend fun applyClientRemote(
        state: ClientSyncState,
        changes: List<SyncRecord>,
        serverVersion: Long,
        now: Long
    ): ClientSyncState {
        val index = state.index.associateBy { it.syncKey }.toMutableMap()
        val sorted = changes.sortedBy { SyncTypes.rank(it.type) }
        store.applyRemoteBatch(sorted, index)
        val current = store.snapshot(index.values.toList())
        return state.copy(
            lastServerVersion = serverVersion,
            index = SyncEngine.buildIndex(current),
            lastSyncAt = now
        )
    }

    suspend fun flushServerLocal(state: ServerSyncState, now: Long): ServerSyncState {
        val current = store.snapshot(state.index)
        val diff = SyncEngine.diff(state.index, current, store.deviceId, now)
        if (diff.changed.isEmpty()) return state
        var version = state.version
        val oldByKey = state.index.associateBy { it.syncKey }
        val tombstones = state.tombstones.toMutableList()
        val changedKeys = diff.changed.filterNot { it.deleted }.map { it.syncKey }.toSet()
        val newIndex = diff.nextIndex.map { entry ->
            if (entry.syncKey in changedKeys) {
                version += 1
                entry.copy(version = version)
            } else {
                val old = oldByKey[entry.syncKey]
                if (old != null) {
                    old.copy(localId = entry.localId, hash = entry.hash, updatedAt = entry.updatedAt)
                } else {
                    version += 1
                    entry.copy(version = version)
                }
            }
        }
        diff.changed.filter { it.deleted }.forEach { record ->
            version += 1
            tombstones += SyncTombstone(
                syncKey = record.syncKey,
                type = record.type,
                version = version,
                deviceId = store.deviceId,
                updatedAt = record.updatedAt
            )
        }
        return state.copy(
            version = version,
            index = newIndex,
            tombstones = tombstones.takeLast(TOMBSTONE_LIMIT)
        )
    }

    suspend fun applyServerRemote(
        state: ServerSyncState,
        changes: List<SyncRecord>,
        now: Long
    ): ServerSyncState {
        var version = state.version
        val index = state.index.associateBy { it.syncKey }.toMutableMap()
        val tombstones = state.tombstones.toMutableList()

        val sorted = changes.sortedBy { SyncTypes.rank(it.type) }
        val results = store.applyRemoteBatch(sorted, index)

        sorted.forEach { record ->
            val localId = results[record.syncKey]
            if (localId != null) {
                version += 1
                if (record.deleted) {
                    tombstones += SyncTombstone(
                        syncKey = record.syncKey,
                        type = record.type,
                        version = version,
                        deviceId = record.deviceId,
                        updatedAt = record.updatedAt
                    )
                } else {
                    index[record.syncKey] = index[record.syncKey]?.copy(version = version) ?: SyncIndexEntry(
                        syncKey = record.syncKey,
                        type = record.type,
                        localId = localId,
                        hash = SyncEngine.hash(record),
                        updatedAt = record.updatedAt,
                        version = version,
                        deviceId = record.deviceId
                    )
                }
            }
        }

        val current = store.snapshot(index.values.toList())
        val currentKeys = current.map { it.record.syncKey }.toSet()
        index.values.filter { it.syncKey !in currentKeys }.forEach { old ->
            version += 1
            tombstones += SyncTombstone(
                syncKey = old.syncKey,
                type = old.type,
                version = version,
                deviceId = store.deviceId,
                updatedAt = now
            )
            index.remove(old.syncKey)
        }

        val newIndex = current.map { snapshot ->
            val old = index[snapshot.record.syncKey]
            if (old != null) {
                old.copy(
                    localId = snapshot.localId,
                    hash = SyncEngine.hash(snapshot.record),
                    updatedAt = snapshot.record.updatedAt
                )
            } else {
                version += 1
                SyncIndexEntry(
                    syncKey = snapshot.record.syncKey,
                    type = snapshot.record.type,
                    localId = snapshot.localId,
                    hash = SyncEngine.hash(snapshot.record),
                    updatedAt = snapshot.record.updatedAt,
                    version = version,
                    deviceId = snapshot.record.deviceId
                )
            }
        }
        return state.copy(
            version = version,
            index = newIndex,
            tombstones = tombstones.takeLast(TOMBSTONE_LIMIT)
        )
    }

    suspend fun changesFor(state: ServerSyncState, baseVersion: Long): SyncChanges {
        val current = store.snapshot(state.index)
        return SyncChanges(
            version = state.version,
            records = SyncEngine.changesSince(state, current, baseVersion)
        )
    }

    private companion object {
        const val TOMBSTONE_LIMIT = 5_000
    }
}
