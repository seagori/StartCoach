package com.seagori.hgr.quick

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.BuildConfig
import com.seagori.hgr.MainActivity
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.data.repository.ConfirmResult
import com.seagori.hgr.domain.Outcome
import com.seagori.hgr.domain.SessionRestoreRoute
import com.seagori.hgr.domain.label
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.shortcut.GoalShortcutHelper
import com.seagori.hgr.ui.components.OptionItem
import com.seagori.hgr.ui.components.OptionList
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.SecondaryButton
import com.seagori.hgr.ui.components.SmallPill
import com.seagori.hgr.ui.theme.AccentDeep
import com.seagori.hgr.ui.theme.Bg
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.StartCoachTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 다른 앱 사용 중 빠른 설정 타일을 눌렀을 때 화면 전환 없이 올라오는 반투명 바텀시트.
 */
class QuickOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.setGravity(Gravity.BOTTOM)

        val container = (application as StartCoachApplication).container
        val repository = container.repository

        setContent {
            val language by AppLocale.language.collectAsStateWithLifecycle()
            val strings = remember(language) { AppLocale.stringsFor(language) }
            val sessionUi by repository.session.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var inCheck by remember { mutableStateOf(false) }
            var outcomeMessage by remember { mutableStateOf<String?>(null) }
            var noGoal by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val activeGoal = container.db.goalDao().observeActive().first().firstOrNull()
                if (activeGoal == null) {
                    noGoal = true
                } else {
                    val existing = repository.session.value
                    if (existing != null && existing.goal.id == activeGoal.id && existing.choices.isNotEmpty()) {
                        inCheck = existing.session.phase == com.seagori.hgr.domain.SessionPhase.CHECKING.name
                    } else {
                        val route = repository.openGoalFromTrigger(activeGoal.id)
                        inCheck = route == SessionRestoreRoute.CHECK
                    }
                }
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                StartCoachTheme {
                    QuickOverlaySheet(
                        noGoal = noGoal,
                        inCheck = inCheck,
                        outcomeMessage = outcomeMessage,
                        goalTitle = sessionUi?.goal?.title,
                        loading = sessionUi?.loading == true,
                        choices = sessionUi?.choices.orEmpty().map { it.id to it.text },
                        selectedId = sessionUi?.selectedId,
                        lastAction = sessionUi?.lastAction,
                        error = sessionUi?.error,
                        onSelect = { id -> scope.launch { repository.selectChoice(id) } },
                        onConfirm = {
                            scope.launch {
                                if (repository.confirmSelection() == ConfirmResult.Check) {
                                    inCheck = true
                                }
                            }
                        },
                        onReload = { scope.launch { repository.reloadSuggestions() } },
                        onOutcome = { outcome ->
                            scope.launch {
                                outcomeMessage = repository.recordOutcome(outcome)
                            }
                        },
                        onOpenApp = {
                            val goalId = sessionUi?.goal?.id
                            val intent = Intent(this@QuickOverlayActivity, MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                if (goalId != null) putExtra(GoalShortcutHelper.EXTRA_GOAL_ID, goalId)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickOverlaySheet(
    noGoal: Boolean,
    inCheck: Boolean,
    outcomeMessage: String?,
    goalTitle: String?,
    loading: Boolean,
    choices: List<Pair<Long, String>>,
    selectedId: Long?,
    lastAction: String?,
    error: String?,
    onSelect: (Long) -> Unit,
    onConfirm: () -> Unit,
    onReload: () -> Unit,
    onOutcome: (Outcome) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                goalTitle?.let { SmallPill(it) } ?: Spacer(Modifier.width(1.dp))
                Row {
                    TextButton(onClick = onOpenApp) {
                        Text(strings.quickTileOpenFull, color = AccentDeep, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.close, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                noGoal -> {
                    Text(strings.homeEmpty, color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(strings.quickTileOpenFull, onClick = onOpenApp)
                }
                inCheck -> {
                    Text(strings.checkHeading, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    lastAction?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = AccentDeep, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (outcomeMessage == null) {
                        OptionList {
                            OptionItem(Outcome.DONE.label(), false) { onOutcome(Outcome.DONE) }
                            OptionItem(Outcome.PARTIAL.label(), false) { onOutcome(Outcome.PARTIAL) }
                            OptionItem(Outcome.NONE.label(), false) { onOutcome(Outcome.NONE) }
                            OptionItem(Outcome.LATER.label(), false) { onOutcome(Outcome.LATER) }
                        }
                    } else {
                        Text(outcomeMessage, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(strings.close, onClick = onDismiss)
                    }
                }
                else -> {
                    Text(strings.suggestionHeading, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(12.dp))
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentDeep
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(strings.suggestionLoading, color = Muted, fontSize = 14.sp)
                        }
                    } else if (choices.isEmpty()) {
                        Text(error ?: strings.suggestionEmpty, color = Muted, fontSize = 14.sp)
                    } else {
                        OptionList {
                            choices.forEach { (id, text) ->
                                OptionItem(text = text, selected = id == selectedId) {
                                    onSelect(id)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(strings.confirmSuggestion, onClick = onConfirm)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(strings.otherSuggestions, modifier = Modifier.fillMaxWidth(), onClick = onReload)
                    }
                }
            }
        }
    }
}
