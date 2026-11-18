package com.seagori.hgr.ui.archive

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seagori.hgr.domain.goalStatusLabel
import com.seagori.hgr.domain.outcomeLabel
import com.seagori.hgr.i18n.LocalStrings
import com.seagori.hgr.ui.components.ScreenHeader
import com.seagori.hgr.ui.components.card
import com.seagori.hgr.ui.theme.Ink
import com.seagori.hgr.ui.theme.Muted

@Composable
fun ArchiveScreen(viewModel: ArchiveViewModel, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
    ) {
        item(key = "header") {
            ScreenHeader(title = strings.archiveTitle, subtitle = strings.archiveSubtitle)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text(strings.close) }
            Spacer(Modifier.height(12.dp))
            Text(strings.archiveGoals, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (ui.goals.isEmpty()) {
                Text(strings.archiveEmpty, color = Muted, fontSize = 13.sp)
            }
        }

        items(ui.goals, key = { "archived_goal_${it.id}" }) { goal ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .card()
                    .padding(16.dp)
            ) {
                Text(goal.title, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(goalStatusLabel(goal.status), color = Muted, fontSize = 13.sp)
                Row {
                    TextButton(onClick = { viewModel.unarchiveGoal(goal) }) {
                        Text(strings.unarchiveAction)
                    }
                }
            }
        }

        item(key = "records_header") {
            Spacer(Modifier.height(22.dp))
            Text(strings.archiveRecords, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (ui.records.isEmpty()) {
                Text(strings.archiveEmpty, color = Muted, fontSize = 13.sp)
            }
        }

        items(ui.records, key = { "archived_record_${it.id}" }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .card()
                    .padding(16.dp)
            ) {
                Text(
                    ui.goalTitles[item.goalId] ?: strings.goalFallback,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(Modifier.height(4.dp))
                Text(item.actionText, color = Ink, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(outcomeLabel(item.outcome), color = Muted, fontSize = 13.sp)
                Row {
                    TextButton(onClick = { viewModel.unarchiveRecord(item) }) {
                        Text(strings.unarchiveAction)
                    }
                }
            }
        }
    }
}
