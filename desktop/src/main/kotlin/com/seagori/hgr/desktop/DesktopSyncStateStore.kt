package com.seagori.hgr.desktop

import com.seagori.hgr.sync.ServerSyncState
import kotlinx.serialization.json.Json
import java.io.File

class DesktopSyncStateStore(baseDir: File) {
    private val dir = File(baseDir, "sync")
    private val file = File(dir, "sync-state.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): ServerSyncState = runCatching {
        if (!file.exists()) return@runCatching ServerSyncState()
        json.decodeFromString(ServerSyncState.serializer(), file.readText())
    }.getOrElse { ServerSyncState() }

    fun save(state: ServerSyncState) {
        runCatching {
            dir.mkdirs()
            DesktopFileSecurity.restrictToOwner(dir, isDirectory = true)
            val temp = File.createTempFile("sync-state-", ".tmp", dir)
            DesktopFileSecurity.restrictToOwner(temp)
            try {
                temp.writeText(json.encodeToString(ServerSyncState.serializer(), state))
                runCatching {
                    java.nio.file.Files.move(
                        temp.toPath(),
                        file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                }.onFailure {
                    java.nio.file.Files.move(
                        temp.toPath(),
                        file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
                DesktopFileSecurity.restrictToOwner(file)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }.onFailure {
            System.err.println("[StartCoach sync] state save failed: ${it.message}")
        }
    }
}
