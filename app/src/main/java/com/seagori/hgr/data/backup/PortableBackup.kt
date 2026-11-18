package com.seagori.hgr.data.backup

import com.seagori.hgr.data.io.SizeLimits
import com.seagori.hgr.data.io.readLimited
import com.seagori.hgr.i18n.AppLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android와 Windows가 함께 쓰는 백업 형식.
 * Room/데스크탑 모델을 그대로 직렬화하지 않고 플랫폼 중립 DTO로 옮긴다.
 *
 * 파일 구조는 기존과 같은 암호화 ZIP이다.
 * - manifest.json: 형식/스키마 버전
 * - payload.bin: AES-GCM으로 암호화된 PortableBackupFile JSON
 */
object PortableBackup {
    const val SCHEMA_VERSION = 2
    const val FORMAT_ENCRYPTED = 2

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PAYLOAD = "payload.bin"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun write(out: OutputStream, backup: PortableBackupFile, password: CharArray) {
        require(password.isNotEmpty()) { AppLocale.strings.backupPasswordRequired }
        val plain = json.encodeToString(PortableBackupFile.serializer(), backup).toByteArray(Charsets.UTF_8)
        val encrypted = try {
            BackupCipher.encrypt(plain, password)
        } finally {
            plain.fill(0)
        }
        val manifest = PortableBackupManifest(
            format = FORMAT_ENCRYPTED,
            schemaVersion = SCHEMA_VERSION,
            appVersion = backup.appVersion,
            exportedAt = backup.exportedAt
        )
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putEntry(ENTRY_MANIFEST, json.encodeToString(PortableBackupManifest.serializer(), manifest))
            zip.putEntry(ENTRY_PAYLOAD, encrypted)
        }
    }

    fun read(input: InputStream, password: CharArray): PortableBackupFile {
        var manifest: PortableBackupManifest? = null
        var payload: ByteArray? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                if (++count > SizeLimits.BACKUP_ENTRIES) error(AppLocale.strings.tooManyZipEntries)
                val name = entry.name.orEmpty()
                requireSafeEntryName(name)
                when (name) {
                    ENTRY_MANIFEST -> {
                        val bytes = zip.readLimited(SizeLimits.BACKUP_DRAIN_BYTES)
                        manifest = json.decodeFromString(
                            PortableBackupManifest.serializer(),
                            bytes.decodeToString()
                        )
                    }
                    ENTRY_PAYLOAD -> payload = zip.readLimited(SizeLimits.BACKUP_BYTES)
                    else -> error(AppLocale.strings.unknownBackupEntry(name))
                }
                entry = zip.nextEntry
            }
        }
        val header = manifest ?: error(AppLocale.strings.backupDataNotFound)
        check(header.schemaVersion == SCHEMA_VERSION) { AppLocale.strings.unsupportedBackupVersion }
        check(header.format == FORMAT_ENCRYPTED) { AppLocale.strings.unsupportedBackupVersion }
        val blob = payload ?: error(AppLocale.strings.missingEncryptedPayload)
        require(password.isNotEmpty()) { AppLocale.strings.backupPasswordRequired }
        val plain = BackupCipher.decrypt(blob, password)
        try {
            return json.decodeFromString(PortableBackupFile.serializer(), plain.decodeToString())
        } finally {
            plain.fill(0)
        }
    }

    private fun requireSafeEntryName(name: String) {
        require(name.isNotBlank()) { AppLocale.strings.invalidBackupEntry }
        require(!name.contains("..")) { AppLocale.strings.invalidBackupPath }
        require(!name.contains('\\')) { AppLocale.strings.invalidBackupPath }
        require(!name.startsWith("/")) { AppLocale.strings.invalidBackupPath }
        require(name == ENTRY_MANIFEST || name == ENTRY_PAYLOAD) { AppLocale.strings.disallowedEntry(name) }
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putEntry(name, content.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}

@Serializable
data class PortableBackupManifest(
    val format: Int,
    val schemaVersion: Int,
    val appVersion: String = "",
    val exportedAt: Long = 0
)

@Serializable
data class PortableSettings(
    val fastApiUrl: String = "",
    val fastModelId: String = "",
    val fastToken: String = "",
    val coachApiUrl: String = "",
    val coachModelId: String = "",
    val coachToken: String = "",
    val coachReasoningEffort: String = "medium",
    val language: String = "SYSTEM",
    val settingsUpdatedAt: Long = 0
)

@Serializable
data class PortableGoal(
    val id: Long,
    val title: String,
    val domain: String = "OTHER",
    val status: String = "ACTIVE",
    val longTermTarget: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class PortableSession(
    val id: Long,
    val goalId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val phase: String = "ENDED",
    val difficultyLevel: Int = 1,
    val selectedSuggestionId: Long? = null
)

@Serializable
data class PortableSuggestion(
    val id: Long,
    val sessionId: Long,
    val goalId: Long,
    val text: String,
    val difficulty: Int = 1,
    val kind: String = "",
    val selected: Boolean = false,
    val source: String = "",
    val createdAt: Long
)

@Serializable
data class PortableExecution(
    val id: Long,
    val sessionId: Long,
    val goalId: Long,
    val suggestionId: Long = 0,
    val actionText: String = "",
    val outcome: String = "NONE",
    val attempted: Boolean = false,
    val count: Int? = null,
    val durationSec: Int? = null,
    val feltDifficulty: Int? = null,
    val isEstimated: Boolean = false,
    val createdAt: Long,
    val stepId: Long? = null,
    val archived: Boolean = false,
    val updatedAt: Long
)

@Serializable
data class PortableMemory(
    val id: Long = 0,
    val goalId: Long,
    val preference: String? = null,
    val difficultyNote: String? = null,
    val lastProgress: String? = null,
    val summary: String? = null,
    val updatedAt: Long
)

@Serializable
data class PortableMaterial(
    val id: Long,
    val goalId: Long,
    val title: String,
    val markdown: String,
    val markdownHash: String,
    val importedAt: Long
)

@Serializable
data class PortableChunk(
    val id: Long,
    val materialId: Long,
    val headingPath: String = "",
    val body: String = "",
    val sortOrder: Int = 0
)

@Serializable
data class PortableProgress(
    val id: Long,
    val goalId: Long,
    val materialId: Long,
    val chunkIndex: Int = 0,
    val updatedAt: Long
)

@Serializable
data class PortablePath(
    val id: Long,
    val goalId: Long,
    val title: String,
    val sourceMaterialId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class PortableStep(
    val id: Long,
    val pathId: Long,
    val sortOrder: Int = 0,
    val title: String,
    val instruction: String = "",
    val criteria: String = "",
    val minutes: Int = 15,
    val difficulty: Int = 1,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sourceHeading: String? = null
)

@Serializable
data class PortableBackupFile(
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val settings: PortableSettings = PortableSettings(),
    val goals: List<PortableGoal> = emptyList(),
    val sessions: List<PortableSession> = emptyList(),
    val suggestions: List<PortableSuggestion> = emptyList(),
    val executions: List<PortableExecution> = emptyList(),
    val memories: List<PortableMemory> = emptyList(),
    val materials: List<PortableMaterial> = emptyList(),
    val materialChunks: List<PortableChunk> = emptyList(),
    val curriculumProgress: List<PortableProgress> = emptyList(),
    val learningPaths: List<PortablePath> = emptyList(),
    val learningSteps: List<PortableStep> = emptyList()
)
