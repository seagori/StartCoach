package com.seagori.hgr.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import java.util.UUID

class AndroidSyncStore(context: Context) {
    private val appContext = context.applicationContext
    private val plainPrefs = appContext.getSharedPreferences(PREFS_PLAIN, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val securePrefs: SharedPreferences? by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_SECURE,
                MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    init {
        migratePlainStateIfNeeded()
        purgePlainSecretsIfNeeded()
    }

    /** 페어링 토큰 등은 암호화 저장소가 있을 때만 저장한다. */
    val secretsStorageAvailable: Boolean
        get() = securePrefs != null

    val deviceId: String
        get() {
            val existing = plainPrefs.getString(KEY_DEVICE_ID, "").orEmpty()
            if (existing.isNotBlank()) return existing
            val created = UUID.randomUUID().toString()
            plainPrefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    val deviceName: String
        get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    var clientState: ClientSyncState
        get() {
            val secure = securePrefs
            val raw = if (secure != null) {
                secure.getString(KEY_STATE, "").orEmpty()
            } else {
                ""
            }
            if (raw.isBlank()) return ClientSyncState()
            return runCatching {
                json.decodeFromString(ClientSyncState.serializer(), raw)
            }.getOrElse { ClientSyncState() }
        }
        set(value) {
            val needsSecrets = value.paired || value.clientToken.isNotBlank()
            if (needsSecrets) {
                val secure = securePrefs
                    ?: throw IllegalStateException("encrypted sync storage unavailable")
                secure.edit()
                    .putString(KEY_STATE, json.encodeToString(ClientSyncState.serializer(), value))
                    .apply()
                plainPrefs.edit().remove(KEY_STATE).apply()
            } else {
                securePrefs?.edit()?.remove(KEY_STATE)?.apply()
                plainPrefs.edit().remove(KEY_STATE).apply()
            }
        }

    fun clearPairing() {
        clientState = ClientSyncState()
    }

    fun resetSyncIndex() {
        val current = clientState
        if (!current.paired && current.index.isEmpty() && current.resumeTransferId.isBlank()) return
        clientState = current.copy(
            lastServerVersion = 0L,
            index = emptyList(),
            resumeTransferId = "",
            resumePushIndex = 0,
            resumeBaseVersion = -1L,
            resumeRecordsHash = "",
            resumeDeleteStamps = emptyMap()
        )
    }

    private fun migratePlainStateIfNeeded() {
        val secure = securePrefs ?: return
        val legacy = plainPrefs.getString(KEY_STATE, "").orEmpty()
        if (legacy.isBlank() || secure.contains(KEY_STATE)) return
        secure.edit().putString(KEY_STATE, legacy).apply()
        plainPrefs.edit().remove(KEY_STATE).apply()
    }

    private fun purgePlainSecretsIfNeeded() {
        if (securePrefs != null) return
        plainPrefs.edit().remove(KEY_STATE).apply()
    }

    private companion object {
        const val PREFS_PLAIN = "startcoach_sync"
        const val PREFS_SECURE = "startcoach_sync_secure"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_STATE = "client_state"
    }
}
