package com.seagori.hgr.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.seagori.hgr.domain.LlmProfile

class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Keystore는 기기 복원/이전 시 사용할 수 없을 수 있다.
     * 앱 시작 시점이 아니라 실제 접근 시점에 만들고, 실패하면 빈 저장소로 동작한다.
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                FILE_NAME,
                MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    fun hasToken(profile: LlmProfile): Boolean {
        migrateLegacyIfNeeded()
        return prefs?.getString(keyBearer(profile), "").orEmpty().isNotBlank()
    }

    /** 토큰은 마지막으로 입력한 origin(스킴+호스트+포트)에만 사용한다. */
    fun tokenFor(profile: LlmProfile, origin: String): String? {
        migrateLegacyIfNeeded()
        val stored = prefs?.getString(keyBearer(profile), "").orEmpty()
        val storedOrigin = prefs?.getString(keyOrigin(profile), "").orEmpty()
        return stored.takeIf {
            it.isNotBlank() && storedOrigin.equals(origin, ignoreCase = true)
        }
    }

    /** origin과 무관하게 저장된 토큰. 백업에만 쓴다. */
    fun rawToken(profile: LlmProfile): String {
        migrateLegacyIfNeeded()
        return prefs?.getString(keyBearer(profile), "").orEmpty()
    }

    fun originFor(profile: LlmProfile): String {
        migrateLegacyIfNeeded()
        return prefs?.getString(keyOrigin(profile), "").orEmpty()
    }

    private fun migrateLegacyIfNeeded() {
        val target = prefs ?: return
        if (target.contains(KEY_BEARER_FAST)) return
        val legacy = target.getString(KEY_LEGACY_BEARER, "").orEmpty()
        if (legacy.isBlank()) return
        target.edit()
            .putString(KEY_BEARER_FAST, legacy)
            .putString(KEY_ORIGIN_FAST, target.getString(KEY_LEGACY_ORIGIN, "").orEmpty())
            .remove(KEY_LEGACY_BEARER)
            .remove(KEY_LEGACY_ORIGIN)
            .apply()
    }

    fun save(profile: LlmProfile, token: String, origin: String): Boolean {
        val target = prefs ?: return false
        target.edit()
            .putString(keyBearer(profile), token.trim())
            .putString(keyOrigin(profile), origin.lowercase())
            .apply()
        return true
    }

    fun clear(profile: LlmProfile) {
        prefs?.edit()
            ?.remove(keyBearer(profile))
            ?.remove(keyOrigin(profile))
            ?.apply()
    }

    private fun keyBearer(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_BEARER_FAST
        LlmProfile.COACH -> KEY_BEARER_COACH
    }

    private fun keyOrigin(profile: LlmProfile) = when (profile) {
        LlmProfile.FAST -> KEY_ORIGIN_FAST
        LlmProfile.COACH -> KEY_ORIGIN_COACH
    }

    private companion object {
        const val FILE_NAME = "startcoach_secure"
        const val KEY_BEARER_FAST = "bearer_token_fast"
        const val KEY_ORIGIN_FAST = "token_origin_fast"
        const val KEY_BEARER_COACH = "bearer_token_coach"
        const val KEY_ORIGIN_COACH = "token_origin_coach"

        // v0.2 단일 토큰 키 (빠른 제안 프로필로 승계)
        const val KEY_LEGACY_BEARER = "bearer_token"
        const val KEY_LEGACY_ORIGIN = "token_origin"
    }
}
