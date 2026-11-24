package com.seagori.hgr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Muted
import java.time.LocalDate
import java.time.YearMonth

private val LightGreen = Color(0xFF8BC34A)
private val LightGreenBorder = LightGreen
private val LightGreenFill = LightGreen
private val DarkGreenFull = Color(0xFF2E7D32)

/**
 * 한 달간 매일 할일 달성률(%)을 원형 채움으로 표시하는 달력 컴포넌트.
 * 0%: 연두색 테두리만, 1~99%: 비율만큼 채움, 100%: 진한 초록색 동그라미.
 */
@Composable
fun DailyCalendarSection(
    dailyRates: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    val today = remember { LocalDate.now() }
    val firstDayOffset = month.atDay(1).dayOfWeek.value - 1 // 월=0 .. 일=6
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((firstDayOffset + daysInMonth + 6) / 7) * 7

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Ink)
            }
            Text(
                text = "${month.year}.${month.monthValue.toString().padStart(2, '0')}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Ink)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dow in 1..7) {
                Text(
                    text = strings.weekdayShort(dow),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        for (rowStart in 0 until totalCells step 7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (offset in 0 until 7) {
                    val dayNum = (rowStart + offset) - firstDayOffset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val rate = dailyRates[date.toString()]?.coerceIn(0, 100)
                            val isToday = date == today
                            DayRateCircle(
                                day = dayNum,
                                rate = rate,
                                isToday = isToday
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = strings.dailyHistoryNote,
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun DayRateCircle(day: Int, rate: Int?, isToday: Boolean) {
    val circlePath = remember { Path() }
    val desc = if (rate != null) "$day, $rate%" else "$day"
    Box(
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        if (rate != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 1.5.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                if (rate >= 100) {
                    drawCircle(color = DarkGreenFull, radius = radius, center = center)
                } else {
                    if (rate > 0) {
                        circlePath.reset()
                        circlePath.addOval(
                            androidx.compose.ui.geometry.Rect(
                                center.x - radius,
                                center.y - radius,
                                center.x + radius,
                                center.y + radius
                            )
                        )
                        val fillHeight = (radius * 2f) * (rate / 100f)
                        clipPath(circlePath) {
                            drawRect(
                                color = LightGreenFill,
                                topLeft = Offset(center.x - radius, center.y + radius - fillHeight),
                                size = Size(radius * 2f, fillHeight)
                            )
                        }
                    }
                    drawCircle(
                        color = LightGreenBorder,
                        radius = radius,
                        center = center,
                        style = Stroke(width = if (isToday) 2.4.dp.toPx() else 1.6.dp.toPx())
                    )
                }
            }
        }
        Text(
            text = "$day",
            fontSize = 11.sp,
            fontWeight = if (isToday || (rate ?: 0) >= 100) FontWeight.Bold else FontWeight.Medium,
            color = when {
                (rate ?: 0) >= 100 -> Color.White
                rate == null && isToday -> AccentDeep
                rate == null -> Muted
                else -> Ink
            }
        )
    }
}
