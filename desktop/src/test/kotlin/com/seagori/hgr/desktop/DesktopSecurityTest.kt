package com.seagori.hgr.desktop

import com.seagori.hgr.data.settings.SettingsSnapshot
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.sync.ServerSyncState
import com.seagori.hgr.sync.SyncController
import com.seagori.hgr.sync.SyncRecord
import com.seagori.hgr.sync.SyncTypes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 데스크탑 비밀정보 보호(암호화 저장, origin 바인딩, 백업 비밀번호) 회귀 테스트. */
class DesktopSecurityTest {
    private val apiUrl = "https://llm.example.invalid/v1"
    private val otherUrl = "https://attacker.example.invalid/v1"

    private fun newRoot(): Preferences =
        Preferences.userRoot().node("com/seagori/hgr/sec-${UUID.randomUUID()}")

    private fun newKeyDir(): File = Files.createTempDirectory("startcoach-sec-test").toFile()

    @Test
    fun apiKeyIsEncryptedAtRest() {
        val root = newRoot()
        val keyDir = newKeyDir()
        try {
            val store = SettingsStore(root, keyDir)
            store.setApiBaseUrl(LlmProfile.FAST, apiUrl)
            store.setToken(LlmProfile.FAST, "very-secret-token")

            val stored = root.get("fast_api_key", "")
            assertTrue(stored.startsWith("enc:v1:"), "토큰은 암호화되어 저장되어야 한다")
            assertTrue(!stored.contains("very-secret-token"), "평문 토큰이 남으면 안 된다")
            assertTrue(keyDir.resolve("desktop-key.bin").exists(), "마스터 키 파일이 생성되어야 한다")

            assertEquals("very-secret-token", store.token(LlmProfile.FAST))
        } finally {
            runCatching { root.removeNode() }
            keyDir.deleteRecursively()
        }
    }

    @Test
    fun tokenIsBoundToOrigin() {
        val root = newRoot()
        val keyDir = newKeyDir()
        try {
            val store = SettingsStore(root, keyDir)
            store.setApiBaseUrl(LlmProfile.FAST, apiUrl)
            store.setToken(LlmProfile.FAST, "bound-token")

            val expected = assertNotNull(UrlRules.origin(apiUrl))
            assertEquals("bound-token", store.tokenFor(LlmProfile.FAST, expected))

            val other = assertNotNull(UrlRules.origin(otherUrl))
            assertNull(store.tokenFor(LlmProfile.FAST, other), "다른 origin으로는 토큰을 쓰면 안 된다")
        } finally {
            runCatching { root.removeNode() }
            keyDir.deleteRecursively()
        }
    }

    @Test
    fun legacyPlaintextTokenIsMigratedAndRebound() {
        val root = newRoot()
        val keyDir = newKeyDir()
        try {
            root.put("fast_api_base_url", apiUrl)
            root.put("fast_api_key", "legacy-plain-token")

            val store = SettingsStore(root, keyDir)
            val stored = root.get("fast_api_key", "")
            assertTrue(stored.startsWith("enc:v1:"), "기존 평문 토큰은 자동으로 암호화되어야 한다")
            assertEquals("legacy-plain-token", store.token(LlmProfile.FAST))
            val origin = assertNotNull(UrlRules.origin(apiUrl))
            assertEquals("legacy-plain-token", store.tokenFor(LlmProfile.FAST, origin))
        } finally {
            runCatching { root.removeNode() }
            keyDir.deleteRecursively()
        }
    }

    @Test
    fun syncDoesNotRebindExistingTokenToNewOrigin() = runBlocking {
        val root = newRoot()
        val keyDir = newKeyDir()
        try {
            val settings = SettingsStore(root, keyDir)
            settings.setApiBaseUrl(LlmProfile.FAST, apiUrl)
            settings.setToken(LlmProfile.FAST, "vault-token")
            var data = DesktopData()
            val store = DesktopSyncStore(
                "device-a",
                { data },
                { transform -> data = transform(data); data },
                settings
            )
            val controller = SyncController(store)

            // 페어링된 기기가 새 URL을 보내면서 토큰은 생략한 경우.
            val stamp = System.currentTimeMillis() + 60_000
            val record = SyncRecord(
                syncKey = "settings",
                type = SyncTypes.SETTINGS,
                deviceId = "peer",
                updatedAt = stamp,
                payload = buildJsonObject {
                    put("fastApiUrl", otherUrl)
                    put("fastToken", "")
                    put("coachApiUrl", "")
                    put("coachToken", "")
                }
            )
            controller.applyServerRemote(ServerSyncState(), listOf(record), stamp)

            assertEquals(otherUrl, settings.apiBaseUrl(LlmProfile.FAST))
            val realOrigin = assertNotNull(UrlRules.origin(apiUrl))
            val evilOrigin = assertNotNull(UrlRules.origin(otherUrl))
            assertEquals("vault-token", settings.tokenFor(LlmProfile.FAST, realOrigin))
            assertNull(
                settings.tokenFor(LlmProfile.FAST, evilOrigin),
                "새 URL로 기존 키가 재바인딩되면 안 된다"
            )
        } finally {
            runCatching { root.removeNode() }
            keyDir.deleteRecursively()
        }
    }

    @Test
    fun backupRejectsShortPassword() {
        val dir = Files.createTempDirectory("startcoach-sec-backup").toFile()
        try {
            val settings = SettingsSnapshot(
                fastApiUrl = "",
                fastModelId = "",
                fastToken = "",
                coachApiUrl = "",
                coachModelId = "",
                coachToken = "",
                coachReasoningEffort = SettingsStore.DEFAULT_REASONING,
                language = AppLanguage.ENGLISH,
                updatedAt = 0L
            )
            val result = DesktopBackup.export(
                File(dir, "backup.zip"),
                DesktopData(),
                settings,
                "short".toCharArray()
            )
            assertTrue(result.isFailure, "8자 미만 비밀번호는 거부되어야 한다")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun urlRulesRejectsLocalAndPrivateIps() {
        assertNull(UrlRules.normalize("https://localhost/v1"))
        assertNull(UrlRules.normalize("https://localhost./v1"))
        assertNull(UrlRules.normalize("https://127.0.0.1/v1"))
        assertNull(UrlRules.normalize("https://10.0.0.1/v1"))
        assertNull(UrlRules.normalize("https://100.64.0.1/v1"))
        assertNull(UrlRules.normalize("https://[::1]/v1"))
        assertNull(UrlRules.normalize("https://[fc00::1]/v1"))
        assertNull(UrlRules.normalize("https://[fd12:3456::1]/v1"))
        assertNotNull(UrlRules.normalize("https://api.example.com/v1"))
    }
}
