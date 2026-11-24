package com.seagori.hgr.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.seagori.hgr.MainActivity
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.shortcut.GoalShortcutHelper
import com.seagori.hgr.ui.theme.WidgetPalette
import com.seagori.hgr.ui.theme.widgetPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StartCoachWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as StartCoachApplication
        val container = app.container
        val strings = AppLocale.strings
        val activeGoals = runCatching { container.db.goalDao().observeActive().first() }.getOrDefault(emptyList())
        val goal = activeGoals.firstOrNull()
        val actionText = if (goal != null) {
            val cached = container.suggestionCache.easiestAction(goal.id)
            val stepTitle = runCatching {
                val path = container.db.learningPathDao().getByGoal(goal.id)
                path?.let { p ->
                    container.db.learningStepDao().listForPath(p.id).firstOrNull { !it.completed }?.title
                }
            }.getOrNull()
            cached ?: stepTitle ?: strings.widgetOpenApp
        } else {
            null
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (goal != null) {
                putExtra(GoalShortcutHelper.EXTRA_GOAL_ID, goal.id)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val palette = widgetPalette(container.settings.appTheme)
        provideContent {
            WidgetContent(
                goalTitle = goal?.title,
                actionText = actionText ?: strings.widgetEmpty,
                hintText = strings.quickStartButton,
                palette = palette,
                intent = intent
            )
        }
    }

    @Composable
    private fun WidgetContent(
        goalTitle: String?,
        actionText: String,
        hintText: String,
        palette: WidgetPalette,
        intent: Intent
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.bg))
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(intent))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (goalTitle != null) {
                Text(
                    text = goalTitle,
                    style = TextStyle(
                        color = ColorProvider(palette.accentDeep),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            Text(
                text = actionText,
                style = TextStyle(
                    color = ColorProvider(palette.ink),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2
            )
            if (goalTitle != null) {
                Spacer(GlanceModifier.height(8.dp))
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ColorProvider(palette.accentLight))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = hintText,
                        style = TextStyle(
                            color = ColorProvider(palette.accentDeep),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    companion object {
        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            updateScope.launch {
                runCatching { StartCoachWidget().updateAll(appContext) }
            }
        }
    }
}

class StartCoachWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StartCoachWidget()
}
