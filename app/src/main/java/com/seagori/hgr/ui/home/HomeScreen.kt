package com.seagori.hgr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.components.GoalCardList
import com.seagori.hgr.ui.components.GoalRow
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.SmallPill
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Insight
import com.seagori.hgr.ui.theme.Line
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStart: () -> Unit,
    onQuickCheck: () -> Unit = onStart
) {
    val strings = LocalStrings.current
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 72.dp, bottom = 24.dp)
    ) {
        Text(
            text = strings.appName,
            fontSize = 28.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        val latest = goals.firstOrNull()
        if (latest != null) {
            val quickAction = latest.easiestCachedAction
                ?: latest.currentStepTitle
                ?: strings.quickStartFallback
            Spacer(Modifier.height(20.dp))
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
                    SmallPill(latest.goal.title)
                    Text(strings.quickStartTitle, fontSize = 11.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(quickAction, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(strings.quickStartButton) {
                    if (latest.easiestCachedAction != null || latest.currentStepTitle != null) {
                        viewModel.startQuickAction(latest.goal.id, quickAction, onQuickCheck, onStart)
                    } else {
                        viewModel.start(latest.goal.title, onStart)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Soft)
                    .border(1.dp, Line, RoundedCornerShape(20.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(1.7.dp, Ink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 16.sp, color = Ink)
                    }
                    Spacer(Modifier.size(9.dp))
                    Text(strings.homeInputHint, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Text("›", color = Ink, fontSize = 22.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Card)
                    .border(1.5.dp, AccentDeep.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(start = 17.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 80) topic = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        viewModel.start(topic) {
                            topic = ""
                            expanded = false
                            onStart()
                        }
                    }),
                    textStyle = TextStyle(fontSize = 18.sp, color = Ink),
                    cursorBrush = SolidColor(AccentDeep),
                    decorationBox = { inner ->
                        if (topic.isEmpty()) {
                            Text(strings.homeInputPlaceholder, color = Muted, fontSize = 18.sp)
                        }
                        inner()
                    }
                )
                IconButton(
                    onClick = {
                        viewModel.start(topic) {
                            topic = ""
                            expanded = false
                            onStart()
                        }
                    },
                    modifier = Modifier
                        .size(43.dp)
                        .background(AccentDeep, CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Card)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = strings.homeShowSuggestions, tint = Card)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clickable { expanded = false }
                        .padding(top = 12.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Muted)
                    Text(strings.close, color = Muted, fontSize = 13.sp)
                }
            }
        }
        if (error != null) {
            Text(error!!, color = AccentDeep, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(48.dp))
        GoalCardList(
            title = strings.homeGoalsTitle,
            items = goals.map {
                GoalRow(
                    title = it.goal.title,
                    subtitle = if (it.totalSteps > 0) {
                        strings.stepProgress(it.completedSteps, it.totalSteps) + " · " +
                            (it.currentStepTitle?.let { title -> strings.nextStepLabel(title) }
                                ?: strings.allStepsDone)
                    } else {
                        strings.homeNewStart
                    },
                    progress = it.progress
                )
            },
            emptyText = strings.homeEmpty,
            onClick = { row -> viewModel.start(row.title, onStart) }
        )
    }
}
