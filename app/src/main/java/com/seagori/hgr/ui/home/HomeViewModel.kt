package com.seagori.hgr.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.repository.HomeGoalItem
import com.seagori.hgr.domain.SessionRestoreRoute
import com.seagori.hgr.i18n.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: CoachRepository
) : ViewModel() {
    val goals: StateFlow<List<HomeGoalItem>> = repository.homeGoals.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var starting = false

    fun start(title: String, onStarted: () -> Unit) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                repository.startOrContinue(title)
                _error.value = null
                onStarted()
            } catch (e: Exception) {
                _error.value = AppLocale.strings.errorEnterTask
            } finally {
                starting = false
            }
        }
    }

    fun startQuickAction(
        goalId: Long,
        actionText: String,
        onCheck: () -> Unit,
        onSuggestion: () -> Unit
    ) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                val route = repository.startQuickAction(goalId, actionText)
                _error.value = null
                when (route) {
                    SessionRestoreRoute.CHECK -> onCheck()
                    SessionRestoreRoute.SUGGESTION -> onSuggestion()
                }
            } catch (e: Exception) {
                _error.value = AppLocale.strings.errorEnterTask
            } finally {
                starting = false
            }
        }
    }
}
