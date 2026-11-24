package com.seagori.hgr.desktop

import com.seagori.hgr.domain.DailyTaskState
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyTasksTest {
    @Test
    fun dailyTasksDoNotAccumulateAcrossDaysAndComputePercent() {
        var state = DailyTaskState()
            .addTask("물 한 잔 마시기", today = "2026-11-22")
            .addTask("스트레칭 5분", today = "2026-11-22")
            .addTask("단어 10개 복습", today = "2026-11-22")

        val firstId = state.tasks[0].id
        val secondId = state.tasks[1].id

        state = state.toggleTask(firstId, today = "2026-11-22")
        assertEquals(33, state.todayPercent("2026-11-22"))
        assertEquals(33, state.dailyRates["2026-11-22"])

        state = state.toggleTask(secondId, today = "2026-11-22")
        assertEquals(67, state.todayPercent("2026-11-22"))

        // 다음 날에는 완료 목록이 리셋되고 누적되지 않는다.
        assertEquals(emptySet(), state.effectiveCompletedIds("2026-11-23"))
        assertEquals(0, state.todayPercent("2026-11-23"))

        // 어제 기록은 그대로 남아 있다.
        assertEquals(67, state.dailyRates["2026-11-22"])
    }
}
