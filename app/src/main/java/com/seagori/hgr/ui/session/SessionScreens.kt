package com.seagori.hgr.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.domain.Outcome
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.components.OptionItem
import com.seagori.hgr.ui.components.OptionList
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.SecondaryButton
import com.seagori.hgr.ui.components.SmallPill
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Insight
import com.seagori.hgr.ui.theme.Muted

@Composable
fun SuggestionScreen(
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onHome: () -> Unit,
    onOpenGoals: () -> Unit
) {
    val strings = LocalStrings.current
    val ui by viewModel.session.collectAsStateWithLifecycle()
    val choices = ui?.choices.orEmpty()
    val clarification = ui?.clarification
    val loading = ui?.loading == true
    var manualAction by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = strings.suggestionTitle, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            SmallPill(ui?.goal?.title ?: strings.goalFallback)
            ui?.step?.takeIf { ui?.stepCount ?: 0 > 0 }?.let { step ->
                Spacer(Modifier.height(8.dp))
                SmallPill(strings.stepProgress(ui?.stepIndex ?: 0, ui?.stepCount ?: 0) + " · " + step.title)
            }
            ui?.curriculumLabel?.takeIf { it.isNotBlank() && ui?.step == null }?.let {
                Spacer(Modifier.height(8.dp))
                SmallPill(it)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .card(22.dp)
                    .padding(17.dp)
            ) {
                Text(strings.suggestionHeading, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                ui?.step?.let { step ->
                    Spacer(Modifier.height(12.dp))
                    Text(step.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    step.instruction.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 13.sp, color = Ink, lineHeight = 20.sp)
                    }
                    ui?.materialExcerpt?.let { excerpt ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            strings.pathMaterialSource,
                            fontSize = 11.sp,
                            color = AccentDeep,
                            fontWeight = FontWeight.Bold
                        )
                        Text(excerpt, fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                    }
                    val criteria = step.criteria.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                    if (criteria.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(strings.stepCriteriaTitle, fontSize = 12.sp, color = AccentDeep, fontWeight = FontWeight.Bold)
                        criteria.forEach {
                            Text("· $it", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if (ui?.step == null) Spacer(Modifier.height(16.dp))
                if (ui != null && ui?.step == null && (ui?.stepCount ?: 0) == 0) {
                    Text(strings.noPathYet, color = Muted, fontSize = 12.sp)
                    TextButton(onClick = onOpenGoals) {
                        Text(strings.buildPath, color = AccentDeep, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    clarification != null -> {
                        Text(
                            clarification.question,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(strings.clarificationHint, color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        OptionList {
                            clarification.options.forEach { option ->
                                OptionItem(option, false) {
                                    manualAction = ""
                                    viewModel.answerClarification(option)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::skipClarification) {
                            Text(strings.skipClarification, color = Muted)
                        }
                    }
                    loading -> {
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
                    choices.isEmpty() -> {
                        Text(
                            strings.suggestionEmpty,
                            color = Muted,
                            fontSize = 14.sp
                        )
                    }
                    else -> {
                        OptionList {
                            choices.forEach { choice ->
                                OptionItem(
                                    text = choice.text,
                                    selected = choice.id == ui?.selectedId,
                                    onClick = {
                                        manualAction = ""
                                        viewModel.select(choice.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            ui?.error?.let { Text(it, color = AccentDeep, fontSize = 12.sp) }
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(
                value = manualAction,
                onValueChange = { manualAction = it },
                label = { Text(strings.customActionLabel) },
                placeholder = { Text(strings.customActionHint) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            if (!loading && clarification == null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    strings.otherSuggestions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    manualAction = ""
                    viewModel.reload()
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            PrimaryButton(
                strings.confirmSuggestion,
                enabled = manualAction.isNotBlank() ||
                    (!loading && clarification == null && choices.isNotEmpty())
            ) {
                if (manualAction.isNotBlank()) {
                    viewModel.confirmCustom(manualAction, onCheck = onCheck, onHome = onHome)
                } else {
                    viewModel.confirm(onCheck = onCheck, onHome = onHome)
                }
            }
        }
    }
}

@Composable
fun CheckScreen(
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNext: () -> Unit
) {
    val strings = LocalStrings.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val result by viewModel.resultMessage.collectAsStateWithLifecycle()
    val pathCompleted by viewModel.pathCompleted.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = strings.checkTitle, onBack = if (pathCompleted) onHome else onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            when {
                pathCompleted -> {
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
                        session?.step?.let { step ->
                            SmallPill(
                                strings.stepProgress(
                                    session?.stepIndex ?: 0,
                                    session?.stepCount ?: 0
                                ) + " · " + step.title
                            )
                            Spacer(Modifier.height(8.dp))
                        } ?: SmallPill(session?.lastAction ?: strings.smallAction)
                        Spacer(Modifier.height(8.dp))
                        Text(strings.checkHeading, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(strings.checkHint, color = Muted, fontSize = 14.sp)
                        session?.step?.let { step ->
                            val criteria = step.criteria.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                            if (criteria.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    strings.stepCriteriaTitle,
                                    color = AccentDeep,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                criteria.forEach {
                                    Text("· $it", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(strings.selfCheckOnly, color = Muted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OptionList {
                            OptionItem(
                                if (session?.step != null) strings.stepDoneAction else strings.outcomeDoneAction,
                                false
                            ) { viewModel.record(Outcome.DONE) }
                            OptionItem(
                                if (session?.step != null) strings.stepPartialAction else strings.outcomePartialAction,
                                false
                            ) { viewModel.record(Outcome.PARTIAL) }
                            OptionItem(strings.outcomeNoneAction, false) { viewModel.record(Outcome.NONE) }
                            OptionItem(strings.outcomeLaterAction, false) { viewModel.record(Outcome.LATER) }
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
                            Text(result!!, fontSize = 15.sp, color = Ink, lineHeight = 24.sp)
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(strings.localOnlyNote, color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (pathCompleted) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                PrimaryButton(strings.navHome) { viewModel.goHome(onHome) }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                SecondaryButton(strings.stop, modifier = Modifier.weight(1f), onClick = { viewModel.stop(onHome) })
                Spacer(Modifier.width(10.dp))
                PrimaryButton(strings.nextSuggestion, modifier = Modifier.weight(1.45f), onClick = {
                    if (result == null) return@PrimaryButton
                    viewModel.next(onNext)
                })
            }
        }
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
