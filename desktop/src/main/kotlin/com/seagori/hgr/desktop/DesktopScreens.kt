package com.seagori.hgr.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.seagori.hgr.domain.ActivityCalendar
import com.seagori.hgr.domain.ActivityEvent
import com.seagori.hgr.domain.DailyTaskItem
import com.seagori.hgr.domain.GoalStatus
import com.seagori.hgr.domain.LlmProfile
import com.seagori.hgr.domain.Outcome
import com.seagori.hgr.domain.goalStatusLabel
import com.seagori.hgr.domain.label
import com.seagori.hgr.domain.outcomeLabel
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.sync.SyncCrypto
import com.seagori.hgr.ui.components.ActivitySection
import com.seagori.hgr.ui.components.AppFilterChip
import com.seagori.hgr.ui.components.DailyCalendarSection
import com.seagori.hgr.ui.components.GoalCardList
import com.seagori.hgr.ui.components.GoalRow
import com.seagori.hgr.ui.components.OptionItem
import com.seagori.hgr.ui.components.OptionList
import com.seagori.hgr.ui.components.PartialMaskVisualTransformation
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.LicenseDialog
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.SecondaryButton
import com.seagori.hgr.ui.components.SmallPill
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.AppThemePreset
import com.seagori.hgr.ui.theme.AccentLight
import com.seagori.hgr.ui.theme.Bg
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Insight
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import java.io.File

private data class DesktopTab(
    val screen: DesktopScreen,
    val label: String,
    val icon: ImageVector
)

@Composable
fun DesktopApp(state: DesktopAppState) {
    val strings = LocalStrings.current
    val tabs = listOf(
        DesktopTab(DesktopScreen.HOME, strings.navHome, Icons.Outlined.Home),
        DesktopTab(DesktopScreen.DAILY, strings.navDaily, Icons.Outlined.CheckCircle),
        DesktopTab(DesktopScreen.RECORDS, strings.navRecords, Icons.AutoMirrored.Outlined.MenuBook),
        DesktopTab(DesktopScreen.GOALS, strings.navGoals, Icons.Outlined.Flag),
        DesktopTab(DesktopScreen.SETTINGS, strings.navSettings, Icons.Outlined.Settings)
    )
    val showBar = state.screen in setOf(
        DesktopScreen.HOME,
        DesktopScreen.DAILY,
        DesktopScreen.RECORDS,
        DesktopScreen.GOALS,
        DesktopScreen.SETTINGS
    )
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = state.screen == tab.screen,
                            onClick = { state.screen = tab.screen },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentDeep,
                                selectedTextColor = AccentDeep,
                                indicatorColor = AccentLight,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                DesktopScreen.HOME -> HomeScreen(state)
                DesktopScreen.DAILY -> DesktopDailyTasksScreen(state)
                DesktopScreen.RECORDS -> RecordsScreen(state)
                DesktopScreen.GOALS -> GoalsScreen(state)
                DesktopScreen.SETTINGS -> SettingsScreen(state)
                DesktopScreen.ARCHIVE -> ArchiveScreen(state)
                DesktopScreen.SUGGESTION -> SuggestionScreen(state)
                DesktopScreen.CHECK -> CheckScreen(state)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    var topic by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 24.dp)
    ) {
        Text(
            text = strings.appName,
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        val latest = state.activeGoals.firstOrNull()
        if (latest != null) {
            val cached = state.easiestCachedAction(latest.id)
            val stepTitle = state.pathForGoal(latest.id)?.current?.title
            val quickAction = cached ?: stepTitle ?: strings.quickStartFallback
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Insight)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallPill(latest.title)
                    Text(strings.quickStartTitle, fontSize = 11.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(quickAction, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(strings.quickStartButton) {
                    if (cached != null || stepTitle != null) {
                        state.startQuickAction(latest.id, quickAction)
                    } else {
                        state.startGoal(latest.title)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = { if (it.length <= 80) topic = it },
            placeholder = { Text(strings.homeInputPlaceholder) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        state.startGoal(topic)
                        if (topic.isNotBlank()) topic = ""
                        true
                    } else {
                        false
                    }
                }
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(strings.homeInputHint) {
            state.startGoal(topic)
            if (topic.isNotBlank()) topic = ""
        }
        state.error?.let {
            Text(it, color = AccentDeep, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(28.dp))
        GoalCardList(
            title = strings.homeGoalsTitle,
            items = state.activeGoals.map { goal ->
                val path = state.pathForGoal(goal.id)
                GoalRow(
                    title = goal.title,
                    subtitle = if (path != null && path.total > 0) {
                        strings.stepProgress(path.completed, path.total) + " · " +
                                (path.current?.let { strings.nextStepLabel(it.title) } ?: strings.allStepsDone)
                    } else {
                        strings.homeNewStart
                    },
                    progress = if (path == null || path.total == 0) 0f else path.completed.toFloat() / path.total
                )
            },
            emptyText = strings.homeEmpty,
            onClick = { row -> state.startGoal(row.title) }
        )
    }
}

@Composable
private fun SuggestionScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    var manual by remember { mutableStateOf("") }
    val clarification = state.clarification
    val step = state.activeStep
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(strings.suggestionTitle) { state.backFromSuggestion() }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            SmallPill(state.activeGoal?.title ?: strings.goalFallback)
            if (step != null) {
                Spacer(Modifier.height(8.dp))
                SmallPill(strings.stepProgress(state.stepIndex, state.stepCount) + " · " + step.title)
            } else {
                state.activeGoal?.let { state.curriculumProgressLine(it.id) }?.let {
                    Spacer(Modifier.height(8.dp))
                    SmallPill(it)
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(22.dp)
                    .padding(17.dp)
            ) {
                Text(strings.suggestionHeading, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (step != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(step.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (step.instruction.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(step.instruction, fontSize = 13.sp, color = Ink, lineHeight = 20.sp)
                    }
                    state.materialExcerpt?.let { excerpt ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.pathMaterialSource,
                            fontSize = 11.sp,
                            color = AccentDeep,
                            fontWeight = FontWeight.Bold
                        )
                        Text(excerpt, fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                    }
                    if (step.criteriaList.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            strings.stepCriteriaTitle,
                            fontSize = 12.sp,
                            color = AccentDeep,
                            fontWeight = FontWeight.Bold
                        )
                        step.criteriaList.forEach {
                            Text("· $it", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Spacer(Modifier.height(16.dp))
                    if (state.stepCount == 0) {
                        Text(strings.noPathYet, color = Muted, fontSize = 12.sp)
                        TextButton(onClick = { state.screen = DesktopScreen.GOALS }) {
                            Text(strings.buildPath, color = AccentDeep, fontSize = 13.sp)
                        }
                    }
                }
                when {
                    clarification != null -> {
                        Text(clarification.question, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(strings.clarificationHint, color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        OptionList {
                            clarification.options.forEach { option ->
                                OptionItem(option, false) {
                                    manual = ""
                                    state.answerClarification(option)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = state::skipClarification) {
                            Text(strings.skipClarification, color = Muted)
                        }
                    }

                    state.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentDeep
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(strings.suggestionLoading, color = Muted, fontSize = 14.sp)
                        }
                    }

                    state.choices.isEmpty() -> {
                        if (state.error == null) Text(strings.suggestionEmpty, color = Muted, fontSize = 14.sp)
                    }

                    else -> {
                        OptionList {
                            state.choices.forEach { choice ->
                                OptionItem(
                                    text = choice.text,
                                    selected = state.selectedChoiceId == choice.id,
                                    onClick = {
                                        manual = ""
                                        state.selectChoice(choice.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            state.error?.let { Text(it, color = AccentDeep, fontSize = 12.sp) }
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text(strings.customActionLabel) },
                placeholder = { Text(strings.customActionHint) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            if (!state.loading && clarification == null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(strings.otherSuggestions, modifier = Modifier.fillMaxWidth()) {
                    manual = ""
                    state.otherSuggestions()
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            PrimaryButton(
                strings.confirmSuggestion,
                enabled = manual.isNotBlank() ||
                        (!state.loading && clarification == null && state.choices.isNotEmpty())
            ) {
                if (manual.isNotBlank()) {
                    state.confirmManual(manual)
                } else {
                    state.confirmSelected()
                }
            }
        }
    }
}

@Composable
private fun CheckScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    val step = state.activeStep
    val result = state.resultMessage
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(strings.checkTitle) {
            if (state.pathCompleted) state.goHome() else state.backFromCheck()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            when {
                state.pathCompleted -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .card(22.dp)
                            .padding(18.dp)
                    ) {
                        Text(strings.allStepsDone, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(strings.allStepsDoneNote, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(strings.selfCheckOnly, color = Muted, fontSize = 12.sp)
                    }
                }

                result == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .card(22.dp)
                            .padding(17.dp)
                    ) {
                        if (step != null) {
                            SmallPill(strings.stepProgress(state.stepIndex, state.stepCount) + " · " + step.title)
                            Spacer(Modifier.height(8.dp))
                        } else {
                            SmallPill(state.activeAction ?: strings.smallAction)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(strings.checkHeading, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(strings.checkHint, color = Muted, fontSize = 14.sp)
                        if (step != null && step.criteriaList.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                strings.stepCriteriaTitle,
                                color = AccentDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            step.criteriaList.forEach {
                                Text("· $it", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(strings.selfCheckOnly, color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        OptionList {
                            OptionItem(
                                if (step != null) strings.stepDoneAction else strings.outcomeDoneAction,
                                false
                            ) { state.record(Outcome.DONE) }
                            OptionItem(
                                if (step != null) strings.stepPartialAction else strings.outcomePartialAction,
                                false
                            ) { state.record(Outcome.PARTIAL) }
                            OptionItem(strings.outcomeNoneAction, false) { state.record(Outcome.NONE) }
                            OptionItem(strings.outcomeLaterAction, false) { state.record(Outcome.LATER) }
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Insight)
                            .padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("▥", fontSize = 29.sp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(strings.recorded, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                            Spacer(Modifier.height(7.dp))
                            Text(result, fontSize = 15.sp, color = Ink, lineHeight = 24.sp)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(strings.localOnlyNote, color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (state.pathCompleted) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                PrimaryButton(strings.navHome) { state.goHome() }
            }
        } else {
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                SecondaryButton(strings.stop, modifier = Modifier.weight(1f)) { state.stopAndHome() }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    strings.nextSuggestion,
                    modifier = Modifier.weight(1.45f),
                    enabled = result != null
                ) { state.next() }
            }
        }
    }
}

@Composable
private fun RecordsScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    val stats = state.growth()
    val paths = remember(state.data.paths, state.data.steps, state.data.goals) { state.pathSummaries() }
    val records = state.records
    val grouped = remember(records) { records.groupBy { state.recordDate(it.createdAt) } }
    val goalTitles = remember(state.data.goals) { state.data.goals.associate { it.id to it.title } }
    val stepTitles = remember(state.data.steps) { state.data.steps.associate { it.id to it.title } }
    val activity = remember(records) {
        ActivityCalendar.build(
            records.map {
                ActivityEvent(
                    timestamp = it.createdAt,
                    done = it.outcome == Outcome.DONE.name,
                    partial = it.outcome == Outcome.PARTIAL.name,
                    durationSec = it.durationSec
                )
            }
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = strings.recordsTitle, subtitle = strings.recordsSubtitle)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(strings.openArchive) { state.openArchive(DesktopScreen.RECORDS) }
            Spacer(Modifier.height(16.dp))
            ActivitySection(activity)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Insight)
                    .padding(18.dp)
            ) {
                Text(strings.growth, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Spacer(Modifier.height(10.dp))
                if (state.totalSteps() > 0) {
                    Text(
                        strings.stepProgress(state.completedSteps(), state.totalSteps()),
                        color = Accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(strings.firstAttempts(stats.firstAttempts), color = Ink, fontSize = 15.sp)
                Text(strings.startSuccessRate((stats.startSuccessRate * 100).toInt()), color = Ink, fontSize = 15.sp)
                Text(strings.actualVolume(stats.actualVolume, stats.estimatedVolume), color = Ink, fontSize = 15.sp)
                Text(strings.difficultyTrend(stats.difficultyTrend), color = Ink, fontSize = 15.sp)
            }
        }
        if (paths.isNotEmpty()) {
            item(key = "paths_header") {
                Spacer(Modifier.height(18.dp))
                Text(strings.pathSection, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
            }
            items(paths, key = { "path_${it.path.id}" }) { summary ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .card()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            summary.goalTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            strings.stepProgress(summary.completed, summary.total),
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(
                        if (summary.total == 0) 0f else summary.completed.toFloat() / summary.total
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        summary.currentStep?.let { strings.nextStepLabel(it) } ?: strings.allStepsDone,
                        color = if (summary.currentStep == null) Accent else Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item(key = "history_header") {
            Spacer(Modifier.height(22.dp))
            if (grouped.isEmpty()) {
                Text(strings.recordsEmpty, color = Muted)
            }
        }
        if (grouped.isNotEmpty()) {
            grouped.forEach { (day, items) ->
                item(key = "day_$day") {
                    Text(
                        "$day · ${strings.activityCount(items.size)}",
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp, top = 6.dp)
                    )
                }
                items(items, key = { "execution_${it.id}" }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .card()
                            .padding(16.dp)
                    ) {
                        Text(
                            goalTitles[item.goalId] ?: strings.goalFallback,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(item.actionText, color = Ink, fontSize = 15.sp)
                        (item.stepId?.let { stepTitles[it] })?.let { stepTitle ->
                            Spacer(Modifier.height(3.dp))
                            Text(stepTitle, color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${outcomeLabel(item.outcome)} · ${if (item.attempted) strings.attempted else strings.notAttempted} · ${if (item.isEstimated) strings.estimated else strings.actual}",
                            color = Muted,
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { state.archiveExecution(item) }) {
                                Text(strings.archiveAction, color = Muted)
                            }
                            TextButton(onClick = { state.deleteExecution(item) }) {
                                Text(strings.delete, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    val visibleGoals = state.visibleGoals
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = strings.goalsTitle)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(strings.newGoal) {
                state.pathMessage = null
                state.importMessage = null
                creating = true
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.openArchive) { state.openArchive(DesktopScreen.GOALS) }
            Spacer(Modifier.height(18.dp))
        }
        items(visibleGoals, key = { "goal_${it.id}" }) { goal ->
            val path = state.pathForGoal(goal.id)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .card()
                    .clickable {
                        state.pathMessage = null
                        state.importMessage = null
                        editingId = goal.id
                    }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goal.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (path != null && path.total > 0) {
                        Text(
                            strings.stepProgress(path.completed, path.total),
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    strings.goalMeta(goalStatusLabel(goal.status), goal.longTermTarget ?: strings.noLongTermTarget),
                    color = Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (path != null && path.total > 0) {
                    ProgressBar(path.completed.toFloat() / path.total)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        path.current?.let { strings.nextStepLabel(it.title) } ?: strings.allStepsDone,
                        color = if (path.current == null) Accent else Muted,
                        fontSize = 12.sp
                    )
                } else {
                    Text(strings.noPathYet, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }

    if (creating) {
        GoalEditorDialog(
            state = state,
            goal = null,
            onDismiss = { creating = false }
        )
    }
    editingId?.let { id ->
        val goal = state.goals.firstOrNull { it.id == id }
        if (goal != null) {
            GoalEditorDialog(
                state = state,
                goal = goal,
                onDismiss = { editingId = null }
            )
        }
    }
}

@Composable
private fun GoalEditorDialog(
    state: DesktopAppState,
    goal: DesktopGoal?,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var title by remember(goal?.id) { mutableStateOf(goal?.title.orEmpty()) }
    var target by remember(goal?.id) { mutableStateOf(goal?.longTermTarget.orEmpty()) }
    var status by remember(goal?.id) {
        mutableStateOf(goal?.status?.let { GoalStatus.valueOf(it) } ?: GoalStatus.ACTIVE)
    }
    val path = goal?.let { state.pathForGoal(it.id) }
    val materials = goal?.let { state.materialsForGoal(it.id) }.orEmpty()
    val pathLoading = goal != null && state.pathLoadingGoalId == goal.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) strings.goalCreateTitle else strings.goalEditTitle) },
        containerColor = Card,
        titleContentColor = Ink,
        textContentColor = Ink,
        tonalElevation = 0.dp,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.goalTitleLabel) })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(strings.longTermTargetLabel) })
                Spacer(Modifier.height(12.dp))
                Row {
                    GoalStatus.editable.forEach { item ->
                        AppFilterChip(
                            selected = status == item,
                            onClick = { status = item },
                            label = item.label(),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                if (goal != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(strings.learningMaterials, fontWeight = FontWeight.Bold, color = Ink)
                    state.curriculumProgressLine(goal.id)?.let {
                        Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    materials.forEach { material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(material.title, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { state.deleteMaterial(material.id, goal.id) }) {
                                Text(strings.delete, color = Muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(strings.importMarkdown) {
                        DesktopFiles.openMarkdown()?.let { state.importMarkdown(goal.id, it) }
                    }
                    state.importMessage?.let {
                        Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(strings.pathSection, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.pathMaterialHint, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (path != null && path.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(path.path.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            strings.stepProgress(path.completed, path.total) +
                                    (path.current?.let { " · " + strings.nextStepLabel(it.title) } ?: ""),
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(path.completed.toFloat() / path.total)
                        Spacer(Modifier.height(8.dp))
                        path.steps.forEachIndexed { index, step ->
                            Text(
                                (if (step.completed) "✓ " else "○ ") + "${index + 1}. ${step.title}",
                                color = if (step.completed) Muted else Ink,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(strings.noPathYet, color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = when {
                            pathLoading -> strings.pathBuilding
                            path == null -> strings.buildPath
                            else -> strings.rebuildPath
                        },
                        loading = pathLoading,
                        enabled = !pathLoading
                    ) {
                        state.buildPath(goal.id, title, target, status)
                    }
                    state.pathMessage?.let {
                        Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    val related = state.relatedRecords(goal.id)
                    if (related.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(strings.relatedRecords, fontWeight = FontWeight.Bold, color = Ink)
                        related.forEach {
                            Text(
                                "· ${it.actionText}",
                                color = Muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    state.saveGoal(goal?.id, title, target, status)
                    onDismiss()
                }
            }) { Text(strings.save) }
        },
        dismissButton = {
            Row {
                if (goal != null) {
                    TextButton(onClick = {
                        state.archiveGoal(goal)
                        onDismiss()
                    }) { Text(strings.archiveAction) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        state.deleteGoal(goal)
                        onDismiss()
                    }) { Text(strings.delete, color = Muted) }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text(strings.close) }
            }
        }
    )
}

@Composable
private fun ArchiveScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    val archivedGoals = state.archivedGoals
    val archivedRecords = state.archivedRecords
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = strings.archiveTitle, subtitle = strings.archiveSubtitle)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { state.screen = state.archiveReturnScreen }) { Text(strings.close) }
            Spacer(Modifier.height(16.dp))
            Text(strings.archiveGoals, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (archivedGoals.isEmpty()) {
                Text(strings.archiveEmpty, color = Muted, fontSize = 13.sp)
            }
        }
        items(archivedGoals, key = { "arch_goal_${it.id}" }) { goal ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .card()
                    .padding(16.dp)
            ) {
                Text(goal.title, fontWeight = FontWeight.SemiBold, color = Ink)
                TextButton(onClick = { state.unarchiveGoal(goal) }) {
                    Text(strings.unarchiveAction)
                }
            }
        }
        item(key = "records_header") {
            Spacer(Modifier.height(22.dp))
            Text(strings.archiveRecords, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (archivedRecords.isEmpty()) {
                Text(strings.archiveEmpty, color = Muted, fontSize = 13.sp)
            }
        }
        items(archivedRecords, key = { "arch_rec_${it.id}" }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .card()
                    .padding(16.dp)
            ) {
                Text(state.goalTitle(item.goalId), fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(item.actionText, color = Ink, fontSize = 15.sp)
                TextButton(onClick = { state.unarchiveExecution(item) }) {
                    Text(strings.unarchiveAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var backupFile by remember { mutableStateOf<File?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var showLicenses by remember { mutableStateOf(false) }
    var licenseSections by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(showLicenses) {
        if (showLicenses && licenseSections.isEmpty()) {
            licenseSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadLicenseSections()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        ScreenHeader(title = strings.settingsTitle)
        Spacer(Modifier.height(18.dp))
        Text(strings.languageSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { language ->
                AppFilterChip(
                    selected = state.language == language,
                    onClick = { state.changeLanguage(language) },
                    label = languageLabel(language, strings.languageSystem)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(strings.themeSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemePreset.entries.forEach { preset ->
                AppFilterChip(
                    selected = state.theme == preset,
                    onClick = { state.updateTheme(preset) },
                    label = when (preset) {
                        AppThemePreset.SAGE -> strings.themeSage
                        AppThemePreset.BLUE -> strings.themeBlue
                        AppThemePreset.DARK -> strings.themeDark
                    }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.modelConnection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(strings.remotePrivacy, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(strings.desktopKeyStorageNote, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        ProfileEditor(
            state = state,
            profile = LlmProfile.FAST,
            title = strings.fastModelSection,
            note = strings.fastModelNote,
            url = state.fastUrl,
            model = state.fastModel,
            keyInput = state.fastKey,
            hasSavedKey = state.hasSavedKey(LlmProfile.FAST),
            savedKeyHint = state.savedKeyHint(LlmProfile.FAST),
            urlPlaceholder = "https://bedrock-mantle.us-east-1.api.aws/openai/v1",
            modelPlaceholder = "google.gemma-4-26b-a4b",
            testing = state.testingProfile == LlmProfile.FAST,
            onUrlChange = { state.fastUrl = it; state.settingsSaved = false },
            onModelChange = { state.fastModel = it; state.settingsSaved = false },
            onKeyChange = { state.fastKey = it; state.settingsSaved = false },
            onClearKey = { state.clearKey(LlmProfile.FAST) }
        )
        Spacer(Modifier.height(18.dp))
        ProfileEditor(
            state = state,
            profile = LlmProfile.COACH,
            title = strings.coachModelSection,
            note = strings.coachModelNote,
            url = state.coachUrl,
            model = state.coachModel,
            keyInput = state.coachKey,
            hasSavedKey = state.hasSavedKey(LlmProfile.COACH),
            savedKeyHint = state.savedKeyHint(LlmProfile.COACH),
            urlPlaceholder = "https://bedrock-mantle.us-east-1.api.aws/openai/v1",
            modelPlaceholder = "google.gemma-4-26b-a4b",
            testing = state.testingProfile == LlmProfile.COACH,
            reasoning = state.coachReasoning,
            onReasoningChange = { state.coachReasoning = it; state.settingsSaved = false },
            onUrlChange = { state.coachUrl = it; state.settingsSaved = false },
            onModelChange = { state.coachModel = it; state.settingsSaved = false },
            onKeyChange = { state.coachKey = it; state.settingsSaved = false },
            onClearKey = { state.clearKey(LlmProfile.COACH) }
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (state.settingsSaved) strings.saved else strings.saveConnection) {
            state.saveSettings()
        }
        state.testMessage?.let {
            Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(28.dp))
        SyncSection(state)
        Spacer(Modifier.height(28.dp))
        Text(strings.dataManagement, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(strings.backupDescription, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(strings.backupExport) {
            DesktopFiles.saveBackup()?.let {
                backupFile = it
                backupPassword = ""
                backupAction = BackupAction.EXPORT
            }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(strings.backupImportReplace) {
            DesktopFiles.openBackup()?.let {
                backupFile = it
                backupPassword = ""
                backupAction = BackupAction.IMPORT
            }
        }
        state.backupMessage?.let {
            Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(strings.clearAllRecords) { state.clearAllData() }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.desktopMiniBarMode, fontWeight = FontWeight.Bold, color = Ink)
            Switch(
                checked = state.miniBarEnabled,
                onCheckedChange = { state.miniBarEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(strings.reminderSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.reminderToggle, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = state.reminderEnabled,
                onCheckedChange = { state.updateReminderEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        if (state.reminderEnabled) {
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderRetryLabel,
                value = state.reminderRetryMinutes,
                presets = listOf(10, 15, 30),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { state.updateReminderRetry(it) }
            )
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderCooldown,
                value = state.reminderCooldownMinutes,
                presets = listOf(60, 120, 180),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { state.updateReminderCooldown(it) }
            )
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderDurationLabel,
                value = state.reminderDurationSeconds,
                presets = listOf(5, 15, 30),
                unitLabel = strings::secondsShort,
                customLabel = strings.customOption,
                onSelect = { state.updateReminderDuration(it) }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.reminderMessage,
                onValueChange = { state.updateReminderMessage(it) },
                label = { Text(strings.reminderMessageLabel) },
                placeholder = { Text(strings.reminderDefaultText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(strings.reminderAutoDismissNote, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(strings.dailyReminderSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.dailyReminderToggle, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = state.dailyReminderEnabled,
                onCheckedChange = { state.updateDailyReminderEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Card,
                    checkedTrackColor = AccentDeep
                )
            )
        }
        if (state.dailyReminderEnabled) {
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderRetryLabel,
                value = state.dailyReminderRetryMinutes,
                presets = listOf(10, 15, 30),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { state.updateDailyReminderRetry(it) }
            )
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderCooldown,
                value = state.dailyReminderCooldownMinutes,
                presets = listOf(60, 120, 180),
                unitLabel = strings::minutesShort,
                customLabel = strings.customOption,
                onSelect = { state.updateDailyReminderCooldown(it) }
            )
            Spacer(Modifier.height(10.dp))
            DesktopIntervalSelector(
                label = strings.reminderDurationLabel,
                value = state.dailyReminderDurationSeconds,
                presets = listOf(5, 15, 30),
                unitLabel = strings::secondsShort,
                customLabel = strings.customOption,
                onSelect = { state.updateDailyReminderDuration(it) }
            )
            Spacer(Modifier.height(6.dp))
            Text("${strings.dailyReminderMessage} · ${strings.reminderAutoDismissNote}", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(strings.licenseSection, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(strings.licenseInfo) { showLicenses = true }
    }

    if (showLicenses && licenseSections.isNotEmpty()) {
        LicenseDialog(
            title = strings.licenseInfo,
            sections = licenseSections,
            closeLabel = strings.close,
            onDismiss = { showLicenses = false }
        )
    }

    if (backupAction != null) {
        val action = backupAction
        AlertDialog(
            onDismissRequest = { backupAction = null },
            title = {
                Text(if (action == BackupAction.EXPORT) strings.backupPasswordTitle else strings.backupImportTitle)
            },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            text = {
                Column {
                    Text(
                        if (action == BackupAction.EXPORT) strings.backupExportExplanation else strings.backupImportExplanation,
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text(strings.backupPasswordTitle) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.backupBusy,
                    onClick = {
                        val file = backupFile
                        if (file != null) {
                            val password = backupPassword.toCharArray()
                            if (action == BackupAction.EXPORT) {
                                state.exportBackup(file, password)
                            } else {
                                state.importBackup(file, password)
                            }
                            backupAction = null
                        }
                    }
                ) {
                    Text(if (action == BackupAction.EXPORT) strings.export else strings.import)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupAction = null }) { Text(strings.cancel) }
            }
        )
    }
}

private enum class BackupAction { EXPORT, IMPORT }

private val LICENSE_FILES = listOf(
    "NOTICE" to "licenses/NOTICE.txt",
    "GPL-3.0" to "licenses/GPL-3.0.txt",
    "CC-BY-SA-4.0" to "licenses/CC-BY-SA-4.0.txt",
    "Third-Party" to "licenses/THIRD_PARTY_NOTICES.txt",
    "Apache-2.0" to "licenses/Apache-2.0.txt",
    "Bouncy Castle" to "licenses/Bouncy-Castle.txt"
)

private fun loadLicenseSections(): List<Pair<String, String>> =
    LICENSE_FILES.map { (label, path) ->
        val body = runCatching {
            object {}.javaClass.classLoader
                ?.getResourceAsStream(path)
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull() ?: path
        label to body
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(
    state: DesktopAppState,
    profile: LlmProfile,
    title: String,
    note: String,
    url: String,
    model: String,
    keyInput: String,
    hasSavedKey: Boolean,
    savedKeyHint: String,
    urlPlaceholder: String,
    modelPlaceholder: String,
    testing: Boolean,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onClearKey: () -> Unit,
    reasoning: String? = null,
    onReasoningChange: (String) -> Unit = {}
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(20.dp)
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(note, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(strings.apiUrlLabel) },
            placeholder = { Text(urlPlaceholder) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text(strings.modelIdLabel) },
            placeholder = { Text(modelPlaceholder) },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = {
            onUrlChange(urlPlaceholder)
            onModelChange(modelPlaceholder)
        }) {
            Text(strings.useDefaultProfile, color = AccentDeep, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = onKeyChange,
            label = { Text(strings.apiKeyLabel) },
            placeholder = {
                Text(if (hasSavedKey) strings.apiKeySavedHint else strings.apiKeyInputHint)
            },
            visualTransformation = PartialMaskVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (hasSavedKey && savedKeyHint.isNotBlank() && keyInput.isBlank()) {
            Spacer(Modifier.height(5.dp))
            Text("${strings.savedKeyLabel}: $savedKeyHint", color = Muted, fontSize = 12.sp)
        }
        if (hasSavedKey) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.removeSavedKey, onClick = onClearKey)
        }
        if (reasoning != null) {
            Spacer(Modifier.height(12.dp))
            Text(strings.coachReasoning, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "medium", "high").forEach { value ->
                    AppFilterChip(
                        selected = reasoning == value,
                        onClick = { onReasoningChange(value) },
                        label = when (value) {
                            "low" -> strings.reasoningLow
                            "high" -> strings.reasoningHigh
                            else -> strings.reasoningMedium
                        }
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(strings.coachReasoningNote, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = if (testing) strings.testingConnection else strings.testConnection,
            loading = testing,
            enabled = !testing
        ) { state.testConnection(profile) }
    }
}

@Composable
private fun SyncSection(state: DesktopAppState) {
    val strings = LocalStrings.current
    val sync = state.sync
    Text(strings.syncSection, fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Text(strings.syncDescription, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    if (!sync.running) {
        PrimaryButton(strings.syncServerOn) { sync.start() }
    } else {
        SecondaryButton(strings.syncServerOff) { sync.stop() }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .card(20.dp)
                .padding(16.dp)
        ) {
            Text(strings.syncPairingCode, color = Muted, fontSize = 12.sp)
            Text(
                sync.pairingCode.chunked(3).joinToString(" "),
                color = AccentDeep,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            val qrText = sync.pairQrText()
            val qrImage = remember(qrText) { qrText?.let { qrImageBitmap(it) } }
            if (qrImage != null) {
                Text(strings.syncQrDesktopTitle, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = qrImage,
                        contentDescription = strings.syncQrDesktopTitle,
                        modifier = Modifier.size(168.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(strings.syncQrHint, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            } else {
                Text(strings.syncQrNoNetwork, color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(strings.syncFingerprint, color = Muted, fontSize = 12.sp)
            Text(SyncCrypto.groupedFingerprint(sync.fingerprint), color = Ink, fontSize = 11.sp)
            Text(strings.syncFingerprintNote, color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("${strings.syncPort}: ${sync.port}", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Text(strings.syncPairedDevices, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (sync.pairedDevices.isEmpty()) {
                Text(strings.syncNoDevices, color = Muted, fontSize = 12.sp)
            } else {
                val format = remember { java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault()) }
                sync.pairedDevices.forEach { device ->
                    val last = device.lastSyncAt?.let { format.format(java.util.Date(it)) } ?: strings.syncNever
                    Text(
                        "${device.name} · ${strings.syncLastSync}: $last",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        TextButton(onClick = sync::refreshPairingCode) {
            Text(strings.syncNewCode, color = AccentDeep, fontSize = 12.sp)
        }
        Text(strings.syncServerHint, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
    sync.lastMessage?.let {
        Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Soft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Accent)
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = LocalStrings.current.back, tint = Ink)
        }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

private fun languageLabel(language: AppLanguage, systemLabel: String): String = when (language) {
    AppLanguage.SYSTEM -> systemLabel
    AppLanguage.KOREAN -> "한국어"
    AppLanguage.JAPANESE -> "日本語"
    AppLanguage.CHINESE_SIMPLIFIED -> "简体中文"
    AppLanguage.CHINESE_TRADITIONAL -> "正體中文"
    AppLanguage.ENGLISH -> "English"
}

/** 페어링 QR을 데스크탑 화면에 그린다. Android가 카메라로 바로 읽는다. */
private fun qrImageBitmap(text: String, size: Int = 512): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1)
    )
    val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) {
        for (x in 0 until size) {
            image.setRGB(x, y, if (matrix[x, y]) 0x000000 else 0xFFFFFF)
        }
    }
    image.toComposeImageBitmap()
}.getOrNull()

@Composable
private fun DesktopDailyTasksScreen(state: DesktopAppState) {
    val strings = LocalStrings.current
    val dailyState = state.data.dailyTasks
    val today = remember { com.seagori.hgr.domain.DailyTaskState.todayDateKey() }
    val completedIds = remember(dailyState, today) { dailyState.effectiveCompletedIds(today) }
    val percent = remember(dailyState, today) { dailyState.todayPercent(today) }
    var newTaskText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.dailyTasksTitle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.dailyTasksSubtitle,
                    color = Muted,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = { showHistory = true }) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = strings.dailyHistoryTitle,
                    tint = AccentDeep
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .card()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.dailyProgressLabel(completedIds.size, dailyState.tasks.size, percent),
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "$percent%",
                    color = AccentDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = AccentDeep,
                trackColor = Soft
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                placeholder = { Text(strings.dailyTaskHint) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val trimmed = newTaskText.trim()
                            if (trimmed.isNotEmpty()) {
                                state.addDailyTask(trimmed)
                                newTaskText = ""
                            }
                            true
                        } else {
                            false
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentDeep)
                    .clickable {
                        val trimmed = newTaskText.trim()
                        if (trimmed.isNotEmpty()) {
                            state.addDailyTask(trimmed)
                            newTaskText = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Card, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (dailyState.tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .card()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.dailyTaskEmpty,
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(dailyState.tasks, key = { it.id }) { task ->
                    DesktopDailyTaskRow(
                        task = task,
                        completed = task.id in completedIds,
                        onToggle = { state.toggleDailyTask(task.id) },
                        onDelete = { state.deleteDailyTask(task.id) }
                    )
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            title = { Text(strings.dailyHistoryTitle, fontWeight = FontWeight.Bold) },
            text = {
                DailyCalendarSection(dailyRates = dailyState.dailyRates)
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) {
                    Text(strings.close, color = AccentDeep)
                }
            }
        )
    }
}

@Composable
private fun DesktopDailyTaskRow(
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
                .clickable(onClick = onToggle)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (completed) FontWeight.Normal else FontWeight.SemiBold,
                textDecoration = if (completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = LocalStrings.current.delete,
                    tint = deleteTint
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopIntervalSelector(
    label: String,
    value: Int,
    presets: List<Int>,
    unitLabel: (Int) -> String,
    customLabel: String,
    onSelect: (Int) -> Unit
) {
    val strings = LocalStrings.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember(value) { mutableStateOf(value.toString()) }
    val isCustom = value !in presets

    Text(label, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { preset ->
            AppFilterChip(
                selected = value == preset,
                onClick = { onSelect(preset) },
                label = unitLabel(preset)
            )
        }
        AppFilterChip(
            selected = isCustom,
            onClick = {
                customInput = value.toString()
                showCustomDialog = true
            },
            label = if (isCustom) "$customLabel (${unitLabel(value)})" else customLabel
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            containerColor = Card,
            titleContentColor = Ink,
            textContentColor = Ink,
            tonalElevation = 0.dp,
            title = { Text(label) },
            text = {
                val currentNum = (customInput.toIntOrNull() ?: value).coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { next -> customInput = next.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { customInput = (currentNum - 5).coerceAtLeast(1).toString() }) {
                            Text("-5", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customInput = (currentNum - 1).coerceAtLeast(1).toString() }) {
                            Text("-1", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        Text(unitLabel(currentNum), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { customInput = (currentNum + 1).coerceAtMost(9999).toString() }) {
                            Text("+1", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { customInput = (currentNum + 5).coerceAtMost(9999).toString() }) {
                            Text("+5", color = AccentDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = customInput.toIntOrNull()
                    if (parsed != null && parsed >= 1) {
                        onSelect(parsed)
                    }
                    showCustomDialog = false
                }) {
                    Text(strings.save, color = AccentDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(strings.cancel, color = Muted)
                }
            }
        )
    }
}
