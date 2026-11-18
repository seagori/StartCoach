package com.seagori.hgr.domain

enum class LlmProfile {
    /** 즉석 제안: 빠르고 짧은 응답 */
    FAST,

    /** 경로 설계와 세션 코치: 더 긴 문맥과 추론 */
    COACH
}

data class SuggestionContext(
    val title: String,
    val domain: GoalDomain,
    val lowered: Boolean,
    val lastProgress: String? = null,
    val lastOutcome: Outcome? = null,
    val curriculumExcerpt: String? = null,
    val memorySummary: String? = null,
    val clarification: String? = null,
    val forceChoices: Boolean = false,
    val longTermTarget: String? = null,
    val stepTitle: String? = null,
    val stepInstruction: String? = null,
    val stepCriteria: List<String> = emptyList(),
    val stepPosition: String? = null,
    val stepMinutes: Int? = null
)

data class PathPlanRequest(
    val title: String,
    val target: String? = null,
    val materialOutline: String? = null,
    val preferredStepCount: Int = 8
)

data class PathPlan(
    val title: String,
    val steps: List<PathStep>
)

data class PathStep(
    val title: String,
    val instruction: String,
    val criteria: List<String>,
    val minutes: Int,
    val difficulty: Int,
    val sourceHeading: String? = null
)

data class Clarification(
    val question: String,
    val options: List<String>
)

sealed interface SuggestionResult {
    data class Choices(val choices: List<Choice>) : SuggestionResult
    data class Clarify(val question: String, val options: List<String>) : SuggestionResult
}


data class CurriculumExcerpt(
    val label: String,
    val text: String
)
