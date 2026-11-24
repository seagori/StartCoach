package com.seagori.hgr.di

import android.content.Context
import com.seagori.hgr.data.backup.BackupManager
import com.seagori.hgr.data.cache.SuggestionCacheStore
import com.seagori.hgr.data.curriculum.CurriculumMemoryService
import com.seagori.hgr.data.daily.DailyTaskStore
import com.seagori.hgr.data.local.AppDatabase
import com.seagori.hgr.data.remote.LlmSuggestionGateway
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.settings.SecureTokenStore
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.shortcut.GoalShortcutHelper
import com.seagori.hgr.sync.AndroidSyncManager
import com.seagori.hgr.ui.theme.AppTheme
import com.seagori.hgr.widget.StartCoachWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db = AppDatabase.create(appContext)
    val settings = SettingsStore(appContext)
    val dailyTaskStore = DailyTaskStore(appContext)
    private val secureTokens = SecureTokenStore(appContext)
    val suggestionCache = SuggestionCacheStore(appContext)
    private val triggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        AppLocale.init(settings.appLanguage)
        AppTheme.init(settings.appTheme)
    }

    private fun refreshTriggers() {
        triggerScope.launch {
            val active = runCatching { db.goalDao().observeActive().first() }.getOrDefault(emptyList())
            GoalShortcutHelper.syncDynamicShortcuts(appContext, active)
            StartCoachWidget.updateAllWidgets(appContext)
        }
    }

    private val llm = LlmSuggestionGateway(settings, secureTokens)
    private val curriculum = CurriculumMemoryService(
        db = db,
        materialDao = db.materialDao(),
        chunkDao = db.materialChunkDao(),
        progressDao = db.curriculumProgressDao()
    )
    private val backup = BackupManager(appContext, db, settings, secureTokens)
    val syncManager = AndroidSyncManager(appContext, db, settings, secureTokens)
    val repository = CoachRepository(
        db = db,
        goalDao = db.goalDao(),
        sessionDao = db.sessionDao(),
        suggestionDao = db.suggestionDao(),
        executionDao = db.executionDao(),
        memoryDao = db.memoryDao(),
        pathDao = db.learningPathDao(),
        stepDao = db.learningStepDao(),
        llm = llm,
        curriculum = curriculum,
        backup = backup,
        settings = settings,
        secureTokens = secureTokens,
        suggestionCache = suggestionCache,
        appContext = appContext,
        onResetSyncIndex = { syncManager.resetSyncIndex() },
        onTriggerRefresh = { refreshTriggers() }
    )

    init {
        refreshTriggers()
    }
}
