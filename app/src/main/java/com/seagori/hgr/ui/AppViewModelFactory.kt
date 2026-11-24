package com.seagori.hgr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.seagori.hgr.data.daily.DailyTaskStore
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.sync.AndroidSyncManager
import com.seagori.hgr.ui.archive.ArchiveViewModel
import com.seagori.hgr.ui.daily.DailyTasksViewModel
import com.seagori.hgr.ui.goals.GoalsViewModel
import com.seagori.hgr.ui.home.HomeViewModel
import com.seagori.hgr.ui.records.RecordsViewModel
import com.seagori.hgr.ui.session.SessionViewModel
import com.seagori.hgr.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val repository: CoachRepository,
    private val syncManager: AndroidSyncManager,
    private val dailyTaskStore: DailyTaskStore,
    private val settings: SettingsStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm = when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository)
            modelClass.isAssignableFrom(DailyTasksViewModel::class.java) -> DailyTasksViewModel(dailyTaskStore)
            modelClass.isAssignableFrom(SessionViewModel::class.java) -> SessionViewModel(repository)
            modelClass.isAssignableFrom(RecordsViewModel::class.java) -> RecordsViewModel(repository)
            modelClass.isAssignableFrom(GoalsViewModel::class.java) -> GoalsViewModel(repository)
            modelClass.isAssignableFrom(ArchiveViewModel::class.java) -> ArchiveViewModel(repository)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository, syncManager, settings)
            else -> error("Unknown ViewModel ${modelClass.name}")
        }
        return vm as T
    }
}
