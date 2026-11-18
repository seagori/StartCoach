package com.seagori.hgr.domain

enum class SessionRestoreRoute {
    SUGGESTION,
    CHECK
}

object SessionStateMachine {
    fun afterSuggestionChosen(kind: SuggestionKind): SessionPhase = when (kind) {
        SuggestionKind.LOWER -> SessionPhase.SUGGESTING
        SuggestionKind.END -> SessionPhase.ENDED
        SuggestionKind.ACTION -> SessionPhase.CHECKING
    }

    fun afterCheck(outcome: Outcome): SessionPhase = when (outcome) {
        Outcome.LATER -> SessionPhase.ENDED
        else -> SessionPhase.FOLLOW_UP
    }

    fun shouldLower(outcome: Outcome?): Boolean = outcome == Outcome.NONE

    fun phaseForNewSuggestions(lastOutcome: Outcome?): SessionPhase =
        if (lastOutcome != null) SessionPhase.FOLLOW_UP else SessionPhase.SUGGESTING

    fun restoreRoute(phase: SessionPhase): SessionRestoreRoute? = when (phase) {
        SessionPhase.CHECKING -> SessionRestoreRoute.CHECK
        SessionPhase.SUGGESTING, SessionPhase.FOLLOW_UP -> SessionRestoreRoute.SUGGESTION
        SessionPhase.ENDED -> null
    }
}
