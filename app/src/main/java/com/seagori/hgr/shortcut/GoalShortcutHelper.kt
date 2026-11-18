package com.seagori.hgr.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.seagori.hgr.MainActivity
import com.seagori.hgr.R
import com.seagori.hgr.data.local.entity.GoalEntity

object GoalShortcutHelper {
    const val EXTRA_GOAL_ID = "com.seagori.hgr.extra.GOAL_ID"
    private const val MAX_DYNAMIC = 3

    fun syncDynamicShortcuts(context: Context, activeGoals: List<GoalEntity>) {
        runCatching {
            val shortcuts = activeGoals.take(MAX_DYNAMIC).map { buildShortcut(context, it) }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pinGoalShortcut(context: Context, goal: GoalEntity): Boolean {
        if (!isPinSupported(context)) return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, buildShortcut(context, goal), null)
        }.getOrDefault(false)
    }

    fun disableGoalShortcut(context: Context, goalId: Long) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf("goal_$goalId"),
                null
            )
        }
    }

    fun clearAllShortcuts(context: Context) {
        runCatching {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }

    private fun buildShortcut(context: Context, goal: GoalEntity): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_GOAL_ID, goal.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, "goal_${goal.id}")
            .setShortLabel(goal.title.take(24))
            .setLongLabel(goal.title.take(48))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
    }
}
