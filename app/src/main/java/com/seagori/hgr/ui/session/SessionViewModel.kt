package com.seagori.hgr.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seagori.hgr.data.repository.CoachRepository
import com.seagori.hgr.data.repository.ConfirmResult
import com.seagori.hgr.data.repository.NextStepResult
import com.seagori.hgr.data.repository.SessionUi
import com.seagori.hgr.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: CoachRepository
) : ViewModel() {
    val session: StateFlow<SessionUi?> = repository.session

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage

    private val _pathCompleted = MutableStateFlow(false)
    val pathCompleted: StateFlow<Boolean> = _pathCompleted

    private var isActionBusy = false

    fun select(id: Long) {
        viewModelScope.launch { repository.selectChoice(id) }
    }

    fun confirm(onCheck: () -> Unit, onHome: () -> Unit) {
        if (isActionBusy) return
        isActionBusy = true
        viewModelScope.launch {
            try {
                when (repository.confirmSelection()) {
                    ConfirmResult.Check -> onCheck()
                    ConfirmResult.Home -> onHome()
                    is ConfirmResult.Reloaded, ConfirmResult.Stay -> Unit
                }
            } finally {
                isActionBusy = false
            }
        }
    }

    fun confirmCustom(text: String, onCheck: () -> Unit, onHome: () -> Unit) {
        if (isActionBusy) return
        isActionBusy = true
        viewModelScope.launch {
            try {
                when (repository.confirmCustomAction(text)) {
                    ConfirmResult.Check -> onCheck()
                    ConfirmResult.Home -> onHome()
                    is ConfirmResult.Reloaded, ConfirmResult.Stay -> Unit
                }
            } finally {
                isActionBusy = false
            }
        }
    }

    fun reload() {
        viewModelScope.launch { repository.reloadSuggestions() }
    }

    fun answerClarification(option: String) {
        viewModelScope.launch { repository.answerClarification(option) }
    }

    fun skipClarification() {
        viewModelScope.launch { repository.skipClarification() }
    }

    fun record(outcome: Outcome) {
        if (isActionBusy) return
        isActionBusy = true
        viewModelScope.launch {
            try {
                _pathCompleted.value = false
                _resultMessage.value = repository.recordOutcome(outcome)
            } finally {
                isActionBusy = false
            }
        }
    }

    fun next(onReady: () -> Unit) {
        if (isActionBusy) return
        isActionBusy = true
        viewModelScope.launch {
            try {
                when (repository.nextAfterCheck()) {
                    NextStepResult.Continue -> {
                        _resultMessage.value = null
                        _pathCompleted.value = false
                        onReady()
                    }
                    NextStepResult.PathDone -> {
                        _resultMessage.value = null
                        _pathCompleted.value = true
                    }
                    NextStepResult.Ended -> Unit
                }
            } finally {
                isActionBusy = false
            }
        }
    }

    fun goHome(onHome: () -> Unit) {
        _pathCompleted.value = false
        _resultMessage.value = null
        onHome()
    }

    fun stop(onHome: () -> Unit) {
        viewModelScope.launch {
            _pathCompleted.value = false
            repository.endSession()
            _resultMessage.value = null
            onHome()
        }
    }
}
