package com.seagori.hgr.domain

object GoalDomains {
    fun infer(title: String): GoalDomain {
        val t = title.lowercase()
        if (EXERCISE.any { t.contains(it) }) return GoalDomain.EXERCISE
        if (STUDY.any { t.contains(it) }) return GoalDomain.STUDY
        if (CRAFT.any { t.contains(it) }) return GoalDomain.CRAFT
        return GoalDomain.OTHER
    }

    private val EXERCISE = listOf(
        "운동", "런닝", "달리기", "헬스", "요가", "스트레칭", "걷기",
        "exercise", "workout", "running", "gym", "yoga", "stretch"
    )
    private val STUDY = listOf(
        "공부", "학습", "시험", "과제", "책", "읽기", "암기",
        "study", "learn", "exam", "homework", "read", "memorize"
    )
    private val CRAFT = listOf(
        "만들", "공예", "그림", "코딩", "프로젝트", "요리", "작업",
        "craft", "build", "draw", "code", "project", "cook", "make"
    )
}
