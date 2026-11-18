package com.seagori.hgr.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.repository.LlmProfileUi
import com.seagori.hgr.data.repository.LlmSettingsUi
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.UrlRules
import com.seagori.hgr.domain.toUserMessage
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.sync.AndroidSyncManager
import com.seagori.hgr.sync.AndroidSyncUi
import com.seagori.hgr.sync.DiscoveredServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUi(
    val llm: LlmSettingsUi = LlmSettingsUi(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val saved: Boolean = false,
    val testingProfile: LlmProfile? = null,
    val testMessage: String? = null,
    val backupMessage: String? = null,
    val backupPassword: String = "",
    val pendingExportUri: Uri? = null,
    val pendingImportUri: Uri? = null
)

class SettingsViewModel(
    private val repository: CoachRepository,
    private val syncManager: AndroidSyncManager
) : ViewModel() {
    private val _ui = MutableStateFlow(
        SettingsUi(llm = repository.llmSettings(), language = repository.appLanguage())
    )
    val ui: StateFlow<SettingsUi> = _ui

    val syncUi: StateFlow<AndroidSyncUi> = syncManager.ui

    private fun update(transform: (SettingsUi) -> SettingsUi) {
        _ui.value = transform(_ui.value)
    }

    private fun updateProfile(profile: LlmProfile, transform: (LlmProfileUi) -> LlmProfileUi) {
        update {
            val llm = when (profile) {
                LlmProfile.FAST -> it.llm.copy(fast = transform(it.llm.fast))
                LlmProfile.COACH -> it.llm.copy(coach = transform(it.llm.coach))
            }
            it.copy(llm = llm, saved = false, testMessage = null)
        }
    }

    fun onLanguageChange(language: AppLanguage) {
        repository.setAppLanguage(language)
        update { it.copy(language = language, testMessage = null, backupMessage = null) }
    }

    fun onUrlChange(profile: LlmProfile, value: String) =
        updateProfile(profile) { it.copy(apiUrl = value) }

    fun onModelChange(profile: LlmProfile, value: String) =
        updateProfile(profile) { it.copy(modelId = value) }

    fun onTokenChange(profile: LlmProfile, value: String) =
        updateProfile(profile) { it.copy(bearerTokenInput = value) }

    fun onReasoningChange(value: String) =
        updateProfile(LlmProfile.COACH) { it.copy(reasoningEffort = value) }

    fun save() {
        persistSettings()
    }

    private fun persistSettings(): Boolean {
        val llm = _ui.value.llm
        val fastUrl = validateUrlOrNull(llm.fast.apiUrl)
        val coachUrl = validateUrlOrNull(llm.coach.apiUrl)
        val tokenWithoutUrl = (llm.fast.bearerTokenInput.isNotBlank() && fastUrl.isNullOrBlank()) ||
            (llm.coach.bearerTokenInput.isNotBlank() && coachUrl.isNullOrBlank())
        if (fastUrl == null || coachUrl == null || tokenWithoutUrl) {
            update { it.copy(testMessage = AppLocale.strings.invalidHttpsUrl) }
            return false
        }
        val tokenStored = repository.saveLlmSettings(
            llm.copy(
                fast = llm.fast.copy(apiUrl = fastUrl),
                coach = llm.coach.copy(apiUrl = coachUrl)
            )
        )
        update {
            it.copy(
                llm = repository.llmSettings(),
                saved = true,
                testMessage = if (tokenStored) it.testMessage
                else AppLocale.strings.secureStoreUnavailable
            )
        }
        return tokenStored
    }

    /** 빈 주소는 "이 프로필 사용 안 함"으로 허용한다. */
    private fun validateUrlOrNull(url: String): String? {
        if (url.isBlank()) return ""
        return UrlRules.normalize(url)
    }

    fun clearToken(profile: LlmProfile) {
        repository.clearToken(profile)
        update {
            val llm = when (profile) {
                LlmProfile.FAST -> it.llm.copy(
                    fast = it.llm.fast.copy(bearerTokenInput = "", hasSavedToken = false, maskedToken = "")
                )
                LlmProfile.COACH -> it.llm.copy(
                    coach = it.llm.coach.copy(bearerTokenInput = "", hasSavedToken = false, maskedToken = "")
                )
            }
            it.copy(llm = llm, testMessage = AppLocale.strings.keyDeleted)
        }
    }

    fun testConnection(profile: LlmProfile) {
        if (_ui.value.testingProfile != null) return
        if (!persistSettings()) return
        update { it.copy(testingProfile = profile, testMessage = null) }
        viewModelScope.launch {
            val result = runCatching { repository.testLlmConnection(profile) }
                .getOrElse { Result.failure(it) }
            update {
                it.copy(
                    testingProfile = null,
                    testMessage = result.fold(
                        { message -> message },
                        { e -> e.toUserMessage(AppLocale.strings.connectionFailed) }
                    )
                )
            }
        }
    }

    private fun processBackup(uri: Uri, successMessage: String, action: suspend (Uri) -> Result<Unit>) {
        viewModelScope.launch {
            val result = action(uri)
            update {
                it.copy(backupMessage = result.fold({ successMessage }, { e -> e.toUserMessage(AppLocale.strings.backupFailed) }))
            }
        }
    }

    fun onBackupPasswordChange(value: String) = update { it.copy(backupPassword = value) }

    fun requestExport(uri: Uri) {
        update { it.copy(pendingExportUri = uri, backupMessage = null) }
    }

    fun requestImport(uri: Uri) {
        update { it.copy(pendingImportUri = uri, backupMessage = null) }
    }

    fun confirmBackupExport() {
        val uri = _ui.value.pendingExportUri ?: return
        val password = _ui.value.backupPassword.toCharArray()
        if (password.size < 8) {
            password.fill('\u0000')
            update { it.copy(backupMessage = AppLocale.strings.backupPasswordMin) }
            return
        }
        processBackup(uri, AppLocale.strings.backupExported) { repository.exportBackup(it, password) }
        update { it.copy(pendingExportUri = null, backupPassword = "") }
    }

    fun confirmBackupImport() {
        val uri = _ui.value.pendingImportUri ?: return
        val password = _ui.value.backupPassword.toCharArray()
        viewModelScope.launch {
            val result = repository.importBackup(uri, password)
            update {
                it.copy(
                    backupMessage = result.fold(
                        { AppLocale.strings.backupImported },
                        { e -> e.toUserMessage(AppLocale.strings.backupFailed) }
                    ),
                    pendingImportUri = null,
                    backupPassword = "",
                    // 백업에 설정이 들어 있으므로 화면 값도 다시 읽는다.
                    llm = repository.llmSettings(),
                    language = repository.appLanguage()
                )
            }
        }
    }

    fun cancelBackupDialog() {
        update { it.copy(pendingExportUri = null, pendingImportUri = null) }
    }

    fun clearData() {
        viewModelScope.launch { repository.clearAllData() }
    }

    fun needsLocalNetworkPermission(): Boolean = syncManager.needsLocalNetworkPermission()

    fun startSyncDiscovery() = syncManager.startDiscovery()

    fun stopSyncDiscovery() = syncManager.stopDiscovery()

    fun pairSync(server: DiscoveredServer, code: String) = syncManager.pair(server, code)

    fun pairSyncManual(address: String, code: String, fingerprint: String) =
        syncManager.pairManual(address, code, fingerprint)

    fun pairSyncFromQr(text: String) = syncManager.pairFromQr(text)

    fun syncNow() = syncManager.syncNow()

    fun forgetSyncServer() = syncManager.forgetServer()

    override fun onCleared() {
        syncManager.stopDiscovery()
        super.onCleared()
    }
}
