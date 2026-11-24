package com.seagori.hgr.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.domain.outcomeLabel
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.components.ActivitySection
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.Accent
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Insight
import com.seagori.hgr.ui.theme.Muted
import com.seagori.hgr.ui.theme.Soft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(viewModel: RecordsViewModel, onOpenArchive: () -> Unit = {}) {
    val strings = LocalStrings.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val grouped = ui.groupedItems
    val goalTitles = ui.goalTitles
    val stepTitles = ui.stepTitles

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ScreenHeader(title = strings.recordsTitle, subtitle = strings.recordsSubtitle)
                }
                TextButton(onClick = onOpenArchive) {
                    Text(strings.openArchive, color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            ActivitySection(ui.activity)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Insight)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(strings.growth, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                if (ui.totalSteps > 0) {
                    Text(
                        strings.stepProgress(ui.completedSteps, ui.totalSteps),
                        color = Accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(strings.firstAttempts(ui.stats.firstAttempts), color = Ink, fontSize = 14.sp)
                Text(strings.startSuccessRate((ui.stats.startSuccessRate * 100).toInt()), color = Ink, fontSize = 14.sp)
                Text(strings.actualVolume(ui.stats.actualVolume, ui.stats.estimatedVolume), color = Ink, fontSize = 14.sp)
                Text(
                    strings.difficultyTrend(ui.stats.difficultyTrend),
                    color = Ink,
                    fontSize = 14.sp
                )
            }
        }

        if (ui.paths.isNotEmpty()) {
            item(key = "paths_header") {
                Spacer(Modifier.height(18.dp))
                Text(strings.pathSection, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
            }
            items(ui.paths, key = { "path_${it.path.id}" }) { summary ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .card()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            goalTitles[summary.path.goalId]?.title ?: strings.goalFallback,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Soft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    if (summary.total == 0) 0f
                                    else summary.completed.toFloat() / summary.total
                                )
                                .height(7.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Accent)
                        )
                    }
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
                        Text(goalTitles[item.goalId]?.title ?: strings.goalFallback, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(item.actionText, color = Ink, fontSize = 15.sp)
                        item.stepId?.let { stepTitles[it] }?.let { stepTitle ->
                            Spacer(Modifier.height(3.dp))
                            Text(stepTitle, color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${outcomeLabel(item.outcome)} · ${if (item.attempted) strings.attempted else strings.notAttempted} · ${if (item.isEstimated) strings.estimated else strings.actual}",
                            color = Muted,
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { viewModel.archive(item) }) {
                                Text(strings.archiveAction, color = Muted)
                            }
                            TextButton(onClick = { viewModel.delete(item) }) {
                                Text(strings.delete, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}
