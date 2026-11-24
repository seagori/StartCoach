package com.seagori.hgr.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.ui.theme.AppThemePreset

/** 동기화/백업에 쓰는 설정 값 묶음. 토큰은 SecureTokenStore가 따로 보관한다. */
data class SettingsSnapshot(
    val fastApiUrl: String,
    val fastModelId: String,
    val coachApiUrl: String,
    val coachModelId: String,
    val coachReasoningEffort: String,
    val language: AppLanguage,
    val updatedAt: Long
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("startcoach_settings", Context.MODE_PRIVATE)

    init {
        // v0.2까지는 단일 프로필이었다. 기존 값을 빠른 제안 프로필로 옮긴다.
        if (!prefs.contains(KEY_FAST_API) && prefs.contains(KEY_LEGACY_API)) {
            editPrefs {
                putString(KEY_FAST_API, prefs.getString(KEY_LEGACY_API, "").orEmpty())
                putString(KEY_FAST_MODEL, prefs.getString(KEY_LEGACY_MODEL, "").orEmpty())
                remove(KEY_LEGACY_API)
                remove(KEY_LEGACY_MODEL)
            }
        }
        // 동기화 이전에 저장된 설정은 시각이 없다. 빈 기기(0)에 지지 않도록 아주 오래된 값으로 표시한다.
        if (!prefs.contains(KEY_SETTINGS_UPDATED_AT) && hasConfiguredSettings()) {
            editPrefs { putLong(KEY_SETTINGS_UPDATED_AT, 1L) }
        }
    }

    private fun hasConfiguredSettings(): Boolean =
        apiBaseUrl(LlmProfile.FAST).isNotBlank() ||
            apiBaseUrl(LlmProfile.COACH).isNotBlank() ||
            modelId(LlmProfile.FAST).isNotBlank() ||
            modelId(LlmProfile.COACH).isNotBlank()

    fun apiBaseUrl(profile: LlmProfile): String =
        prefs.getString(keyApi(profile), "").orEmpty()

    fun setApiBaseUrl(profile: LlmProfile, value: String) {
        editSettings { putString(keyApi(profile), value.trim()) }
    }

    fun modelId(profile: LlmProfile): String =
        prefs.getString(keyModel(profile), "").orEmpty()

    fun setModelId(profile: LlmProfile, value: String) {
        editSettings { putString(keyModel(profile), value.trim()) }
    }

    /** GLM 5 같은 추론 모델의 reasoning_effort. 비어 있으면 모델별 기본값을 쓴다. */
    var coachReasoningEffort: String
        get() = prefs.getString(KEY_COACH_REASONING, DEFAULT_REASONING).orEmpty()
        set(value) {
            editSettings { putString(KEY_COACH_REASONING, value.trim()) }
        }

    var appLanguage: AppLanguage
        get() = AppLanguage.fromStored(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name))
        set(value) {
            editSettings { putString(KEY_LANGUAGE, value.name) }
        }

    /** LWW 비교에 쓰는 마지막 변경 시각. 값이 없으면 0이라 첫 동기화에서는 원격이 이긴다. */
    var settingsUpdatedAt: Long
        get() = prefs.getLong(KEY_SETTINGS_UPDATED_AT, 0L)
        set(value) {
            editPrefs { putLong(KEY_SETTINGS_UPDATED_AT, value) }
        }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        fastApiUrl = apiBaseUrl(LlmProfile.FAST),
        fastModelId = modelId(LlmProfile.FAST),
        coachApiUrl = apiBaseUrl(LlmProfile.COACH),
        coachModelId = modelId(LlmProfile.COACH),
        coachReasoningEffort = coachReasoningEffort,
        language = appLanguage,
        updatedAt = settingsUpdatedAt
    )

    /** 원격/백업 값을 그대로 반영한다. 변경 시각을 다시 올리지 않는다. */
    fun applySnapshot(snapshot: SettingsSnapshot) {
        editPrefs {
            putString(KEY_FAST_API, snapshot.fastApiUrl.trim())
            putString(KEY_FAST_MODEL, snapshot.fastModelId.trim())
            putString(KEY_COACH_API, snapshot.coachApiUrl.trim())
            putString(KEY_COACH_MODEL, snapshot.coachModelId.trim())
            putString(KEY_COACH_REASONING, snapshot.coachReasoningEffort.trim())
            putString(KEY_LANGUAGE, snapshot.language.name)
            putLong(KEY_SETTINGS_UPDATED_AT, snapshot.updatedAt)
        }
    }

    fun touch(now: Long = System.currentTimeMillis()) {
        editPrefs { putLong(KEY_SETTINGS_UPDATED_AT, now) }
    }

    private fun keyApi(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_API
        LlmProfile.COACH -> KEY_COACH_API
    }

    private fun keyModel(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_FAST_MODEL
        LlmProfile.COACH -> KEY_COACH_MODEL
    }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        set(value) = editPrefs { putBoolean(KEY_REMINDER_ENABLED, value) }

    var reminderCooldownMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_COOLDOWN_MINUTES, DEFAULT_REMINDER_COOLDOWN_MINUTES)
        set(value) = editPrefs { putInt(KEY_REMINDER_COOLDOWN_MINUTES, value.coerceAtLeast(1)) }

    var reminderRetryMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_RETRY_MINUTES, DEFAULT_REMINDER_RETRY_MINUTES)
        set(value) = editPrefs { putInt(KEY_REMINDER_RETRY_MINUTES, value.coerceAtLeast(1)) }

    var reminderDurationSeconds: Int
        get() = prefs.getInt(KEY_REMINDER_DURATION_SECONDS, DEFAULT_REMINDER_DURATION_SECONDS)
        set(value) = editPrefs { putInt(KEY_REMINDER_DURATION_SECONDS, value.coerceAtLeast(1)) }

    var reminderMessage: String
        get() = prefs.getString(KEY_REMINDER_MESSAGE, "").orEmpty()
        set(value) = editPrefs { putString(KEY_REMINDER_MESSAGE, value) }

    var lastReminderAt: Long
        get() = prefs.getLong(KEY_LAST_REMINDER_AT, 0L)
        set(value) = editPrefs { putLong(KEY_LAST_REMINDER_AT, value) }

    var dailyReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_REMINDER_ENABLED, false)
        set(value) = editPrefs { putBoolean(KEY_DAILY_REMINDER_ENABLED, value) }

    var dailyReminderCooldownMinutes: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_COOLDOWN_MINUTES, DEFAULT_REMINDER_COOLDOWN_MINUTES)
        set(value) = editPrefs { putInt(KEY_DAILY_REMINDER_COOLDOWN_MINUTES, value.coerceAtLeast(1)) }

    var dailyReminderRetryMinutes: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_RETRY_MINUTES, DEFAULT_REMINDER_RETRY_MINUTES)
        set(value) = editPrefs { putInt(KEY_DAILY_REMINDER_RETRY_MINUTES, value.coerceAtLeast(1)) }

    var dailyReminderDurationSeconds: Int
        get() = prefs.getInt(KEY_DAILY_REMINDER_DURATION_SECONDS, DEFAULT_REMINDER_DURATION_SECONDS)
        set(value) = editPrefs { putInt(KEY_DAILY_REMINDER_DURATION_SECONDS, value.coerceAtLeast(1)) }

    var lastDailyReminderAt: Long
        get() = prefs.getLong(KEY_LAST_DAILY_REMINDER_AT, 0L)
        set(value) = editPrefs { putLong(KEY_LAST_DAILY_REMINDER_AT, value) }

    var pinnedDailyNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PINNED_DAILY_NOTIFICATION, false)
        set(value) = editPrefs { putBoolean(KEY_PINNED_DAILY_NOTIFICATION, value) }

    var appTheme: AppThemePreset
        get() = AppThemePreset.fromStored(prefs.getString(KEY_APP_THEME, null))
        set(value) = editPrefs { putString(KEY_APP_THEME, value.name) }

    /** 사용자가 값을 바꾼 경우. LWW 타임스탬프를 함께 올린다. */
    private fun editSettings(block: SharedPreferences.Editor.() -> Unit) {
        editPrefs {
            block()
            putLong(KEY_SETTINGS_UPDATED_AT, System.currentTimeMillis())
        }
    }

    private fun editPrefs(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    companion object {
        const val DEFAULT_REASONING = "medium"
        const val DEFAULT_REMINDER_COOLDOWN_MINUTES = 60
        const val DEFAULT_REMINDER_RETRY_MINUTES = 15
        const val DEFAULT_REMINDER_DURATION_SECONDS = 5

        private const val KEY_FAST_API = "fast_api_base_url"
        private const val KEY_FAST_MODEL = "fast_model_id"
        private const val KEY_COACH_API = "coach_api_base_url"
        private const val KEY_COACH_MODEL = "coach_model_id"
        private const val KEY_COACH_REASONING = "coach_reasoning_effort"
        private const val KEY_LANGUAGE = "app_language"
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
        private const val KEY_PINNED_DAILY_NOTIFICATION = "pinned_daily_notification_enabled"
        private const val KEY_APP_THEME = "app_theme"

        // v0.2 단일 프로필 키 (마이그레이션 전용)
        private const val KEY_LEGACY_API = "api_base_url"
        private const val KEY_LEGACY_MODEL = "llm_model_id"
    }
}
