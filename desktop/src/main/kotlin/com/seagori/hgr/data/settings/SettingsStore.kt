package com.seagori.hgr.data.settings

import com.seagori.hgr.desktop.DesktopFileSecurity
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.ui.theme.AppThemePreset
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 동기화/백업에 쓰는 설정 값 묶음. 데스크탑은 API 키도 여기서 관리한다. */
data class SettingsSnapshot(
    val fastApiUrl: String,
    val fastModelId: String,
    val fastToken: String,
    val coachApiUrl: String,
    val coachModelId: String,
    val coachToken: String,
    val coachReasoningEffort: String,
    val language: AppLanguage,
    val updatedAt: Long
)

/**
 * 데스크탑 설정 저장소.
 * API 키는 로컬 마스터 키(소유자 전용 권한)로 AES-256-GCM 암호화해 Preferences에 저장하고
 * 마지막으로 입력한 origin(스킴+호스트+포트)에 묶어 다른 호스트로 전송되지 않도록 한다.
 */
class SettingsStore(
    private val prefs: java.util.prefs.Preferences =
        Preferences.userRoot().node("com/seagori/hgr/desktop"),
    private val keyDir: File = File(System.getProperty("user.home"), ".startcoach")
) {
    private val secretKey: SecretKey by lazy { loadOrCreateKey() }
    init {
        // v0.2 단일 프로필 값을 빠른 제안 프로필로 승계한다.
        if (prefs.get(KEY_FAST_API, null) == null) {
            val legacyApi = prefs.get(KEY_LEGACY_API, "")
            if (legacyApi.isNotBlank()) {
                prefs.put(KEY_FAST_API, legacyApi)
                prefs.put(KEY_FAST_MODEL, prefs.get(KEY_LEGACY_MODEL, ""))
            } else {
                prefs.put(KEY_FAST_API, "")
            }
        }
        if (prefs.get(KEY_FAST_KEY, null) == null) {
            val legacyKey = prefs.get(KEY_LEGACY_KEY, "")
            if (legacyKey.isNotBlank()) {
                prefs.put(KEY_FAST_KEY, encryptToken(legacyKey))
                UrlRules.origin(prefs.get(KEY_FAST_API, ""))?.let {
                    prefs.put(KEY_FAST_ORIGIN, it.lowercase())
                }
                prefs.remove(KEY_LEGACY_KEY)
            } else {
                prefs.put(KEY_FAST_KEY, "")
            }
        }
        migratePlaintextToken(LlmProfile.FAST)
        migratePlaintextToken(LlmProfile.COACH)
        // 동기화 이전에 저장된 설정은 시각이 없다. 빈 기기(0)에 지지 않도록 아주 오래된 값으로 표시한다.
        if (prefs.get(KEY_SETTINGS_UPDATED_AT, null) == null && hasConfiguredSettings()) {
            prefs.putLong(KEY_SETTINGS_UPDATED_AT, 1L)
        }
    }

    private fun hasConfiguredSettings(): Boolean =
        prefs.get(KEY_FAST_API, "").isNotBlank() ||
            prefs.get(KEY_COACH_API, "").isNotBlank() ||
            prefs.get(KEY_FAST_MODEL, "").isNotBlank() ||
            prefs.get(KEY_COACH_MODEL, "").isNotBlank()

    fun apiBaseUrl(profile: LlmProfile): String = prefs.get(keyApi(profile), "")

    fun setApiBaseUrl(profile: LlmProfile, value: String) {
        putValue(keyApi(profile), value.trim())
    }

    fun modelId(profile: LlmProfile): String = prefs.get(keyModel(profile), "")

    fun setModelId(profile: LlmProfile, value: String) {
        putValue(keyModel(profile), value.trim())
    }

    fun token(profile: LlmProfile): String {
        val raw = prefs.get(keyToken(profile), "")
        if (raw.isBlank()) return ""
        return decryptToken(raw)
    }

    /** 토큰은 마지막으로 입력한 origin(스킴+호스트+포트)에만 사용한다. */
    fun tokenFor(profile: LlmProfile, origin: String): String? {
        val stored = token(profile)
        if (stored.isBlank()) return null
        val storedOrigin = prefs.get(keyOrigin(profile), "")
        return stored.takeIf { storedOrigin.equals(origin, ignoreCase = true) }
    }

    fun setToken(profile: LlmProfile, value: String, origin: String = "") {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            putValue(keyToken(profile), "")
            prefs.put(keyOrigin(profile), "")
            return
        }
        putValue(keyToken(profile), encryptToken(trimmed))
        val resolvedOrigin = origin.ifBlank { UrlRules.origin(apiBaseUrl(profile)).orEmpty() }
        prefs.put(keyOrigin(profile), resolvedOrigin.lowercase())
    }

    var coachReasoningEffort: String
        get() = prefs.get(KEY_COACH_REASONING, DEFAULT_REASONING).ifBlank { DEFAULT_REASONING }
        set(value) {
            putValue(KEY_COACH_REASONING, value.trim())
        }

    var appLanguage: AppLanguage
        get() = AppLanguage.fromStored(prefs.get(KEY_LANGUAGE, AppLanguage.SYSTEM.name))
        set(value) {
            putValue(KEY_LANGUAGE, value.name)
        }

    /** LWW 비교에 쓰는 마지막 변경 시각. 값이 없으면 0이라 첫 동기화에서는 원격이 이긴다. */
    var settingsUpdatedAt: Long
        get() = prefs.getLong(KEY_SETTINGS_UPDATED_AT, 0L)
        set(value) {
            prefs.putLong(KEY_SETTINGS_UPDATED_AT, value)
        }

    val syncDeviceId: String
        get() {
            val existing = prefs.get(KEY_SYNC_DEVICE_ID, "")
            if (existing.isNotBlank()) return existing
            val created = java.util.UUID.randomUUID().toString()
            prefs.put(KEY_SYNC_DEVICE_ID, created)
            return created
        }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        fastApiUrl = apiBaseUrl(LlmProfile.FAST),
        fastModelId = modelId(LlmProfile.FAST),
        fastToken = token(LlmProfile.FAST),
        coachApiUrl = apiBaseUrl(LlmProfile.COACH),
        coachModelId = modelId(LlmProfile.COACH),
        coachToken = token(LlmProfile.COACH),
        coachReasoningEffort = coachReasoningEffort,
        language = appLanguage,
        updatedAt = settingsUpdatedAt
    )

    /**
     * 원격/백업 값을 그대로 반영한다. 변경 시각을 다시 올리지 않는다.
     * [rebindPreservedTokens]가 false면 이미 저장된 토큰이 그대로 전달되어도 origin을 바꾸지 않는다.
     */
    fun applySnapshot(snapshot: SettingsSnapshot, rebindPreservedTokens: Boolean = true) {
        val fastUrl = normalizeUrl(snapshot.fastApiUrl)
        val coachUrl = normalizeUrl(snapshot.coachApiUrl)
        prefs.put(KEY_FAST_API, fastUrl)
        prefs.put(KEY_FAST_MODEL, snapshot.fastModelId.trim())
        applySnapshotToken(LlmProfile.FAST, snapshot.fastToken.trim(), fastUrl, rebindPreservedTokens)
        prefs.put(KEY_COACH_API, coachUrl)
        prefs.put(KEY_COACH_MODEL, snapshot.coachModelId.trim())
        applySnapshotToken(LlmProfile.COACH, snapshot.coachToken.trim(), coachUrl, rebindPreservedTokens)
        prefs.put(KEY_COACH_REASONING, snapshot.coachReasoningEffort.trim())
        prefs.put(KEY_LANGUAGE, snapshot.language.name)
        prefs.putLong(KEY_SETTINGS_UPDATED_AT, snapshot.updatedAt)
    }

    /** 빈 값은 그대로 두고, 그 외에는 https URL만 허용한다. 유효하지 않으면 빈 값으로 둔다. */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return UrlRules.normalize(trimmed).orEmpty()
    }

    private fun applySnapshotToken(
        profile: LlmProfile,
        token: String,
        apiUrl: String,
        rebindPreserved: Boolean
    ) {
        if (token.isEmpty()) {
            prefs.put(keyToken(profile), "")
            prefs.put(keyOrigin(profile), "")
            return
        }
        // 동기화로 기존 토큰이 그대로 전달되면 origin을 바꾸지 않는다(다른 URL로 유출 방지).
        if (!rebindPreserved) {
            val existing = token(profile)
            val existingOrigin = prefs.get(keyOrigin(profile), "")
            if (existing == token && existingOrigin.isNotBlank()) return
        }
        // URL이 없거나 유효하지 않으면 origin 없이 저장한다. 사용처가 없으므로 전송되지 않는다.
        val origin = UrlRules.origin(apiUrl)?.lowercase().orEmpty()
        prefs.put(keyToken(profile), encryptToken(token))
        prefs.put(keyOrigin(profile), origin)
    }

    fun touch(now: Long = System.currentTimeMillis()) {
        prefs.putLong(KEY_SETTINGS_UPDATED_AT, now)
    }

    private fun keyApi(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_API
        LlmProfile.COACH -> KEY_COACH_API
    }

    private fun keyModel(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_MODEL
        LlmProfile.COACH -> KEY_COACH_MODEL
    }

    private fun keyToken(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_KEY
        LlmProfile.COACH -> KEY_COACH_KEY
    }

    private fun keyOrigin(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_ORIGIN
        LlmProfile.COACH -> KEY_COACH_ORIGIN
    }

    private fun migratePlaintextToken(profile: LlmProfile) {
        val raw = prefs.get(keyToken(profile), "")
        if (raw.isNotBlank() && !raw.startsWith(ENC_PREFIX)) {
            prefs.put(keyToken(profile), encryptToken(raw.trim()))
            if (prefs.get(keyOrigin(profile), "").isBlank()) {
                UrlRules.origin(apiBaseUrl(profile))?.let {
                    prefs.put(keyOrigin(profile), it.lowercase())
                }
            }
        }
    }

    private fun encryptToken(plain: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return ENC_PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decryptToken(stored: String): String {
        if (!stored.startsWith(ENC_PREFIX)) return stored
        return runCatching {
            val blob = Base64.getDecoder().decode(stored.removePrefix(ENC_PREFIX))
            if (blob.size <= GCM_IV_BYTES + 16) return ""
            val iv = blob.copyOfRange(0, GCM_IV_BYTES)
            val payload = blob.copyOfRange(GCM_IV_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(payload).decodeToString()
        }.getOrDefault("")
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyFile = File(keyDir, "desktop-key.bin")
        if (keyFile.exists()) {
            DesktopFileSecurity.restrictToOwner(keyDir, isDirectory = true)
            DesktopFileSecurity.restrictToOwner(keyFile)
            val bytes = runCatching { keyFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.size == AES_KEY_BYTES) {
                return SecretKeySpec(bytes, "AES")
            }
        }
        keyDir.mkdirs()
        DesktopFileSecurity.restrictToOwner(keyDir, isDirectory = true)
        val keyBytes = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        keyFile.writeBytes(keyBytes)
        DesktopFileSecurity.restrictToOwner(keyFile)
        return SecretKeySpec(keyBytes, "AES")
    }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_REMINDER_ENABLED, value)

    var reminderCooldownMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_COOLDOWN_MINUTES, DEFAULT_REMINDER_COOLDOWN_MINUTES).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_REMINDER_COOLDOWN_MINUTES, value.coerceAtLeast(1))

    var reminderRetryMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_RETRY_MINUTES, DEFAULT_REMINDER_RETRY_MINUTES).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_REMINDER_RETRY_MINUTES, value.coerceAtLeast(1))

    var reminderDurationSeconds: Int
        get() = prefs.getInt(KEY_REMINDER_DURATION_SECONDS, DEFAULT_REMINDER_DURATION_SECONDS).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_REMINDER_DURATION_SECONDS, value.coerceAtLeast(1))

    var reminderMessage: String
        get() = prefs.get(KEY_REMINDER_MESSAGE, "")
        set(value) = prefs.put(KEY_REMINDER_MESSAGE, value)

    var lastReminderAt: Long
        get() = prefs.getLong(KEY_LAST_REMINDER_AT, 0L)
        set(value) = prefs.putLong(KEY_LAST_REMINDER_AT, value)

    var dailyReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_DAILY_REMINDER_ENABLED, value)

    var dailyReminderCooldownMinutes: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_COOLDOWN_MINUTES, DEFAULT_REMINDER_COOLDOWN_MINUTES).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_DAILY_REMINDER_COOLDOWN_MINUTES, value.coerceAtLeast(1))

    var dailyReminderRetryMinutes: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_RETRY_MINUTES, DEFAULT_REMINDER_RETRY_MINUTES).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_DAILY_REMINDER_RETRY_MINUTES, value.coerceAtLeast(1))

    var dailyReminderDurationSeconds: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_DURATION_SECONDS, DEFAULT_REMINDER_DURATION_SECONDS).coerceAtLeast(1)
        set(value) = prefs.putInt(KEY_DAILY_REMINDER_DURATION_SECONDS, value.coerceAtLeast(1))

    var lastDailyReminderAt: Long
        get() = prefs.getLong(KEY_LAST_DAILY_REMINDER_AT, 0L)
        set(value) = prefs.putLong(KEY_LAST_DAILY_REMINDER_AT, value)

    var appTheme: AppThemePreset
        get() = AppThemePreset.fromStored(prefs.get(KEY_APP_THEME, null))
        set(value) = prefs.put(KEY_APP_THEME, value.name)

    /** 사용자가 값을 바꾼 경우. LWW 타임스탬프를 함께 올린다. */
    private fun putValue(key: String, value: String) {
        prefs.put(key, value)
        touch()
    }

    companion object {
        const val DEFAULT_REASONING = "medium"
        const val DEFAULT_REMINDER_COOLDOWN_MINUTES = 60
        const val DEFAULT_REMINDER_RETRY_MINUTES = 15
        const val DEFAULT_REMINDER_DURATION_SECONDS = 5

        private const val KEY_FAST_API = "fast_api_base_url"
        private const val KEY_FAST_MODEL = "fast_model_id"
        private const val KEY_FAST_KEY = "fast_api_key"
        private const val KEY_FAST_ORIGIN = "fast_token_origin"
        private const val KEY_COACH_API = "coach_api_base_url"
        private const val KEY_COACH_MODEL = "coach_model_id"
        private const val KEY_COACH_KEY = "coach_api_key"
        private const val KEY_COACH_ORIGIN = "coach_token_origin"
        private const val KEY_COACH_REASONING = "coach_reasoning_effort"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SYNC_DEVICE_ID = "sync_device_id"
        private const val KEY_SETTINGS_UPDATED_AT = "settings_updated_at"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_COOLDOWN_MINUTES = "reminder_cooldown_minutes"
        private const val KEY_REMINDER_RETRY_MINUTES = "reminder_retry_minutes"
        private const val KEY_REMINDER_DURATION_SECONDS = "reminder_duration_seconds"
        private const val KEY_REMINDER_MESSAGE = "reminder_message"
        private const val KEY_LAST_REMINDER_AT = "last_reminder_at"
        private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_enabled"
        private const val KEY_DAILY_REMINDER_COOLDOWN_MINUTES = "daily_reminder_cooldown_minutes"
        private const val KEY_DAILY_REMINDER_RETRY_MINUTES = "daily_reminder_retry_minutes"
        private const val KEY_DAILY_REMINDER_DURATION_SECONDS = "daily_reminder_duration_seconds"
        private const val KEY_LAST_DAILY_REMINDER_AT = "last_daily_reminder_at"
        private const val KEY_APP_THEME = "app_theme"

        private const val ENC_PREFIX = "enc:v1:"
        private const val AES_KEY_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        // v0.2 단일 프로필 키 (마이그레이션 전용)
        private const val KEY_LEGACY_API = "api_base_url"
        private const val KEY_LEGACY_MODEL = "llm_model_id"
        private const val KEY_LEGACY_KEY = "api_key"
    }
}
