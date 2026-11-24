package com.seagori.hgr.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.domain.DailyTaskItem
import com.seagori.hgr.domain.DailyTaskState
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.components.DailyCalendarSection
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.AccentLight
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Insight
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import kotlin.math.roundToInt

@Composable
fun DailyTasksScreen(viewModel: DailyTasksViewModel) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { DailyTaskState.todayDateKey() }
    val completedIds = remember(state, today) { state.effectiveCompletedIds(today) }
    val percent = remember(state, today) { state.todayPercent(today) }
    val doneCount = remember(state, completedIds) { state.tasks.count { it.id in completedIds } }
    var input by rememberSaveable { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 28.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ScreenHeader(title = strings.dailyTasksTitle, subtitle = strings.dailyTasksSubtitle)
                }
                IconButton(onClick = { showHistory = true }) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = strings.dailyHistoryTitle,
                        tint = AccentDeep
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Insight)
                    .padding(16.dp)
            ) {
                Text(
                    text = strings.dailyProgressLabel(doneCount, state.tasks.size, percent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Ink
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Soft)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (percent >= 100) Color(0xFF2E7D32) else Accent)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 80) input = it },
                    placeholder = { Text(strings.dailyTaskHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (input.isNotBlank()) {
                            viewModel.addTask(input)
                            input = ""
                        }
                    }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.addTask(input)
                            input = ""
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(AccentDeep, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Card)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = strings.dailyTaskAdd, tint = Card)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (state.tasks.isEmpty()) {
                Text(
                    strings.dailyTaskEmpty,
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        items(state.tasks, key = { "daily_${it.id}" }) { task ->
            val completed = task.id in completedIds
            SwipeableDailyTaskRow(
                task = task,
                completed = completed,
                onToggle = { viewModel.toggleTask(task.id) },
                onDelete = { viewModel.deleteTask(task.id) }
            )
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(strings.dailyHistoryTitle) },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            text = {
                DailyCalendarSection(dailyRates = state.dailyRates)
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
private fun SwipeableDailyTaskRow(
    task: DailyTaskItem,
    completed: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var dragOffset by remember(task.id) { mutableFloatStateOf(0f) }
    var swipedThisGesture by remember(task.id) { mutableStateOf(false) }
    val boxColor = if (completed) Soft else AccentDeep
    val textColor = if (completed) Muted else Color.White
    val deleteTint = if (completed) Muted else Color.White.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .background(if (completed) AccentLight else Color(0xFF1F4D31))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(boxColor)
                .pointerInput(task.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipedThisGesture = false },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset = (dragOffset + dragAmount).coerceIn(-120f, 120f)
                            if (!swipedThisGesture && kotlin.math.abs(dragOffset) > 72f) {
                                swipedThisGesture = true
                                onToggle()
                            }
                        },
                        onDragEnd = { dragOffset = 0f },
                        onDragCancel = { dragOffset = 0f }
                    )
                }
                .clickable { onToggle() }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = if (completed) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = LocalStrings.current.delete,
                    tint = deleteTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
