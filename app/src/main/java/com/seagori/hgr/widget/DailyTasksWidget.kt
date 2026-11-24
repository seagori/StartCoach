package com.seagori.hgr.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.seagori.hgr.MainActivity
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.domain.DailyTaskItem
import com.seagori.hgr.domain.DailyTaskState
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.ui.theme.WidgetPalette
import com.seagori.hgr.ui.theme.widgetPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyTasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as StartCoachApplication).container
        val store = container.dailyTaskStore
        val state = store.state.value
        val today = DailyTaskState.todayDateKey()
        val completedIds = state.effectiveCompletedIds(today)
        val percent = state.todayPercent(today)
        val strings = AppLocale.strings
        val palette = widgetPalette(container.settings.appTheme)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            DailyWidgetContent(
                title = strings.dailyTasksTitle,
                percentText = "$percent%",
                emptyText = strings.dailyTaskEmpty,
                tasks = state.tasks,
                completedIds = completedIds,
                palette = palette,
                launchIntent = launchIntent
            )
        }
    }

    @Composable
    private fun DailyWidgetContent(
        title: String,
        percentText: String,
        emptyText: String,
        tasks: List<DailyTaskItem>,
        completedIds: Set<Long>,
        palette: WidgetPalette,
        launchIntent: Intent
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.bg)
                .cornerRadius(22.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(launchIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(palette.ink),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = percentText,
                    style = TextStyle(
                        color = ColorProvider(palette.accentDeep),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (tasks.isEmpty()) {
                Text(
                    text = emptyText,
                    style = TextStyle(
                        color = ColorProvider(palette.muted),
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.clickable(actionStartActivity(launchIntent))
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(tasks, itemId = { it.id }) { task ->
                        val completed = task.id in completedIds
                        val toggleAction = actionRunCallback<ToggleDailyTaskAction>(
                            actionParametersOf(TASK_ID_KEY to task.id)
                        )
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(toggleAction)
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckBox(
                                checked = completed,
                                onCheckedChange = toggleAction,
                                text = task.title,
                                style = TextStyle(
                                    color = ColorProvider(
                                        if (completed) palette.muted else palette.ink
                                    ),
                                    fontSize = 13.sp,
                                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ColorProvider(palette.accentDeep),
                                    uncheckedColor = ColorProvider(palette.accentDeep)
                                ),
                                modifier = GlanceModifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val TASK_ID_KEY = ActionParameters.Key<Long>("daily_task_id")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                runCatching { DailyTasksWidget().updateAll(appContext) }
            }
        }
    }
}

class ToggleDailyTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[DailyTasksWidget.TASK_ID_KEY] ?: return
        (context.applicationContext as StartCoachApplication).container.dailyTaskStore.toggleTask(taskId)
        DailyTasksWidget().updateAll(context.applicationContext)
    }
}

class DailyTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyTasksWidget()
}
