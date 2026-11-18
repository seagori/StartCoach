package com.seagori.hgr.ui.goals

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.data.local.entity.GoalEntity
import com.seagori.hgr.domain.GoalStatus
import com.seagori.hgr.domain.goalStatusLabel
import com.seagori.hgr.domain.label
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.shortcut.GoalShortcutHelper
import com.seagori.hgr.ui.components.AppFilterChip
import com.seagori.hgr.ui.components.PrimaryButton
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.SecondaryButton
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.Card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft

@Composable
fun GoalsScreen(viewModel: GoalsViewModel, onOpenArchive: () -> Unit = {}) {
    val strings = LocalStrings.current
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val pathSummaries by viewModel.pathSummaries.collectAsStateWithLifecycle()
    val records by viewModel.relatedRecords.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = strings.goalsTitle)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(strings.newGoal) { creating = true }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(strings.openArchive, onClick = onOpenArchive)
            Spacer(Modifier.height(18.dp))
        }
        items(goals, key = { "goal_${it.id}" }) { goal ->
            val path = pathSummaries[goal.id]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .card()
                    .clickable {
                        viewModel.select(goal.id)
                        editing = goal
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
                    strings.goalMeta(
                        goalStatusLabel(goal.status),
                        goal.longTermTarget ?: strings.noLongTermTarget
                    ),
                    color = Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                if (path != null && path.total > 0) {
                    StepProgressBar(path.completed.toFloat() / path.total)
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
        if (records.isNotEmpty()) {
            item(key = "related_header") {
                Spacer(Modifier.height(12.dp))
                Text(strings.relatedRecords, fontWeight = FontWeight.Bold, color = Ink)
            }
            items(records.take(8), key = { "rel_record_${it.id}" }) { record ->
                Text("· ${record.actionText}", color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (creating || editing != null) {
        GoalEditorDialog(
            initial = editing,
            detail = detail,
            onRefresh = viewModel::refreshMaterials,
            onImportMarkdown = { goalId, uri -> viewModel.importMarkdown(goalId, uri) },
            onDeleteMaterial = { materialId, goalId -> viewModel.deleteMaterial(materialId, goalId) },
            onBuildPath = { goalId, title, target, status ->
                viewModel.saveAndBuildPath(goalId, title, target, status)
            },
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { title, target, status ->
                viewModel.save(editing?.id, title, target, status)
                creating = false
                editing = null
            },
            onArchive = editing?.let { goal ->
                {
                    viewModel.archive(goal)
                    creating = false
                    editing = null
                }
            },
            onDelete = editing?.let { goal ->
                {
                    viewModel.delete(goal)
                    creating = false
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun StepProgressBar(progress: Float) {
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
private fun GoalEditorDialog(
    initial: GoalEntity?,
    detail: GoalDetailUi,
    onRefresh: (Long) -> Unit,
    onImportMarkdown: (Long, Uri) -> Unit,
    onDeleteMaterial: (Long, Long) -> Unit,
    onBuildPath: (Long, String, String?, GoalStatus) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String?, GoalStatus) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)?
) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var target by remember { mutableStateOf(initial?.longTermTarget.orEmpty()) }
    var status by remember {
        mutableStateOf(
            initial?.status?.let { runCatching { GoalStatus.valueOf(it) }.getOrNull() }
                ?.takeIf { it != GoalStatus.ARCHIVED }
                ?: GoalStatus.ACTIVE
        )
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val goalId = initial?.id ?: return@rememberLauncherForActivityResult
        uri?.let { onImportMarkdown(goalId, it) }
    }

    // 편집 진입 시 자료 목록을 최신 상태로 갱신 (삭제/가져오기 후 갱신은 VM이 수행)
    LaunchedEffect(initial?.id) { initial?.id?.let(onRefresh) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) strings.goalCreateTitle else strings.goalEditTitle) },
        containerColor = Card,
        titleContentColor = Ink,
        textContentColor = Ink,
        tonalElevation = 0.dp,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(strings.goalTitleLabel) })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text(strings.longTermTargetLabel) })
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
                if (initial != null) {
                    val context = LocalContext.current
                    if (GoalShortcutHelper.isPinSupported(context)) {
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(strings.pinGoalShortcut) {
                            GoalShortcutHelper.pinGoalShortcut(context, initial)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(strings.learningMaterials, fontWeight = FontWeight.Bold, color = Ink)
                    detail.progressLine?.let {
                        Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    detail.materials.forEach { material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(material.title, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { onDeleteMaterial(material.id, initial.id) }) {
                                Text(strings.delete, color = Muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(strings.importMarkdown) {
                        importLauncher.launch(arrayOf("text/*", "text/markdown", "application/octet-stream"))
                    }
                    detail.importMessage?.let {
                        Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(strings.pathSection, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.pathMaterialHint, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    val path = detail.path
                    if (path != null && path.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            path.path.title,
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            strings.stepProgress(path.completed, path.total) +
                                (path.current?.let { " · " + strings.nextStepLabel(it.title) } ?: ""),
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        StepProgressBar(path.completed.toFloat() / path.total)
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
                            detail.pathLoading -> strings.pathBuilding
                            path == null -> strings.buildPath
                            else -> strings.rebuildPath
                        },
                        loading = detail.pathLoading,
                        enabled = !detail.pathLoading
                    ) {
                        onBuildPath(initial.id, title, target, status)
                    }
                    detail.pathMessage?.let {
                        Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onSave(title, target, status) }) { Text(strings.save) }
        },
        dismissButton = {
            Row {
                if (onArchive != null) {
                    TextButton(onClick = onArchive) { Text(strings.archiveAction) }
                    Spacer(Modifier.width(4.dp))
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(strings.delete) }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text(strings.close) }
            }
        }
    )
}
