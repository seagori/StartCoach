package com.seagori.hgr.desktop

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 데스크탑 데이터를 단일 JSON 파일로 저장한다. */
class DesktopStore {
    private val dir = File(System.getProperty("user.home"), ".startcoach")
    private val file = File(dir, "desktop-data.json")
    private val saveLock = Any()
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): DesktopData = synchronized(saveLock) {
        runCatching {
            if (!file.exists()) return@runCatching DesktopData()
            json.decodeFromString(DesktopData.serializer(), file.readText())
        }.getOrElse { DesktopData() }
    }

    fun save(data: DesktopData) = synchronized(saveLock) {
        runCatching {
            dir.mkdirs()
            DesktopFileSecurity.restrictToOwner(dir, isDirectory = true)
            val temp = File.createTempFile("desktop-data-", ".tmp", dir)
            DesktopFileSecurity.restrictToOwner(temp)
            try {
                temp.writeText(json.encodeToString(DesktopData.serializer(), data))
                runCatching {
                    Files.move(
                        temp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                }.onFailure {
                    // 일부 파일 시스템은 ATOMIC_MOVE를 지원하지 않는다.
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                DesktopFileSecurity.restrictToOwner(file)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }.onFailure {
            // 저장 실패가 UI를 멈추지 않게 한다. 다음 저장에서 다시 시도한다.
            System.err.println("[StartCoach] save failed: ${it.message}")
        }
    }
}
