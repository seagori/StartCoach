package com.seagori.hgr.domain

enum class GoalDomain { EXERCISE, STUDY, CRAFT, OTHER }

enum class GoalStatus {
    ACTIVE, PAUSED, COMPLETED, ARCHIVED;

    companion object {
        val editable: List<GoalStatus> = listOf(ACTIVE, PAUSED, COMPLETED)
    }
}

enum class SessionPhase { SUGGESTING, CHECKING, FOLLOW_UP, ENDED }

enum class SuggestionKind { ACTION, LOWER, END }

enum class Outcome { DONE, PARTIAL, NONE, LATER }

val Outcome.isAttempted: Boolean
    get() = this == Outcome.DONE || this == Outcome.PARTIAL

data class Choice(
    val text: String,
    val difficulty: Int,
    val kind: SuggestionKind
)

data class GrowthStats(
    val firstAttempts: Int,
    val startSuccessRate: Float,
    val actualVolume: Int,
    val estimatedVolume: Int,
    val difficultyTrend: Int
)
