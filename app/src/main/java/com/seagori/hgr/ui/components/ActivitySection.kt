package com.seagori.hgr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagori.hgr.domain.ActivityDay
import com.seagori.hgr.domain.ActivitySummary
import com.seagori.hgr.i18n.AppStrings
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Line
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import kotlin.math.roundToInt

/**
 * 삼성 헬스/듀오링고처럼 최근 2주 활동을 막대와 연속 기록으로 보여 준다.
 * 막대를 누르면 그날의 시도/완료/시간을 확인할 수 있다.
 */
@Composable
fun ActivitySection(summary: ActivitySummary, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val lastIndex = (summary.days.size - 1).coerceAtLeast(0)
    var selectedIndex by remember(summary) { mutableStateOf(lastIndex) }
    val selected = summary.days.getOrNull(selectedIndex)
    val today = summary.days.lastOrNull()?.date
    val percent = (summary.achievement * 100).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .card(22.dp)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    strings.lastTwoWeeks,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    strings.activeDaysLabel(summary.activeDays, summary.days.size),
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (strings.streakPrefix.isNotBlank()) {
                    Text(
                        strings.streakPrefix,
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    "${summary.currentStreak}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentDeep
                )
                if (strings.streakSuffix.isNotBlank()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        strings.streakSuffix,
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.achievementLabel(percent),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentDeep
            )
            Spacer(Modifier.weight(1f))
            if (summary.totalCompletions > 0) {
                Text(
                    strings.completionCount(summary.totalCompletions),
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Soft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.achievement.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Accent)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = selected?.let { dayLabel(it, today, strings) } ?: strings.noActivity,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected?.active == true) Ink else Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        ActivityBars(
            summary = summary,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it }
        )

        if (summary.totalAttempts > 0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.thisWeekAttempts(summary.thisWeek),
                    fontSize = 11.sp,
                    color = Muted
                )
                Text(" · ", fontSize = 11.sp, color = Muted)
                Text(
                    strings.lastWeekAttempts(summary.lastWeek),
                    fontSize = 11.sp,
                    color = Muted
                )
                Spacer(Modifier.weight(1f))
                val delta = summary.weekDeltaPercent
                Text(
                    text = if (delta == null) strings.noLastWeek else strings.weekDelta(delta),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (delta != null && delta > 0) Accent else Muted,
                    maxLines = 1
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(strings.activityEmptyNote, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun ActivityBars(
    summary: ActivitySummary,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val strings = LocalStrings.current
    val max = summary.bestDay.coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        summary.days.forEachIndexed { index, day ->
            val isSelected = index == selectedIndex
            val isToday = index == summary.days.lastIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (day.attempts == 0) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 2.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Muted else Line)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .fillMaxHeight((day.attempts.toFloat() / max).coerceIn(0.14f, 1f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        isSelected -> AccentDeep
                                        isToday -> Accent
                                        else -> Accent.copy(alpha = 0.5f)
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = strings.weekdayShort(day.date.dayOfWeek.value),
                    fontSize = 9.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday || isSelected) AccentDeep else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun dayLabel(day: ActivityDay, today: java.time.LocalDate?, strings: AppStrings): String {
    val date = "${day.date.monthValue}/${day.date.dayOfMonth}"
    val weekday = strings.weekdayShort(day.date.dayOfWeek.value)
    val prefix = if (today != null && day.date == today) "${strings.todayLabel} · " else ""
    if (!day.active) {
        val label = if (today != null && day.date == today) strings.todayNoActivity else strings.noActivity
        return "$prefix$weekday $date · $label"
    }
    val parts = mutableListOf(prefix + weekday, date, strings.activityCount(day.attempts))
    if (day.completions > 0) parts += strings.completionCount(day.completions)
    if (day.minutes > 0) parts += strings.minuteCount(day.minutes)
    return parts.joinToString(" · ")
}
