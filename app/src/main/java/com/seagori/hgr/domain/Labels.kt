package com.seagori.hgr.domain

import com.seagori.hgr.i18n.AppLocale

fun Outcome.label(): String = when (this) {
    Outcome.DONE -> AppLocale.strings.outcomeDoneShort
    Outcome.PARTIAL -> AppLocale.strings.outcomePartialShort
    Outcome.NONE -> AppLocale.strings.outcomeNoneShort
    Outcome.LATER -> AppLocale.strings.outcomeLaterShort
}

fun GoalStatus.label(): String = when (this) {
    GoalStatus.ACTIVE -> AppLocale.strings.statusActive
    GoalStatus.PAUSED -> AppLocale.strings.statusPaused
    GoalStatus.COMPLETED -> AppLocale.strings.statusCompleted
    GoalStatus.ARCHIVED -> AppLocale.strings.statusArchived
}

fun outcomeLabel(value: String): String =
    Outcome.entries.firstOrNull { it.name == value }?.label() ?: value

fun goalStatusLabel(value: String): String =
    GoalStatus.entries.firstOrNull { it.name == value }?.label() ?: value
