package com.seagori.hgr.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.seagori.hgr.MainActivity
import com.seagori.hgr.R
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.domain.DailyTaskState
import com.seagori.hgr.i18n.AppLocale

/**
 * 알림창에 오늘 할일 진행도를 1줄 + 진행도 바 형태로 조용히 고정 표시한다.
 */
object DailyProgressNotification {
    private const val CHANNEL_ID = "startcoach_pinned_daily_progress"
    private const val NOTIFICATION_ID = 45873

    fun update(context: Context, settings: SettingsStore = SettingsStore(context)) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.pinnedDailyNotificationEnabled) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(manager)

        val dailyState = (appContext as StartCoachApplication).container.dailyTaskStore.state.value
        val today = DailyTaskState.todayDateKey()
        val doneCount = dailyState.effectiveCompletedIds(today).size
        val totalCount = dailyState.tasks.size
        val percent = dailyState.todayPercent(today)
        val strings = AppLocale.strings

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(appContext, DailyProgressDismissReceiver::class.java)
        val deletePending = PendingIntent.getBroadcast(
            appContext,
            NOTIFICATION_ID,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentTitle(strings.dailyProgressLabel(doneCount, totalCount, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pending)
            .setDeleteIntent(deletePending)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppLocale.strings.pinnedDailySection,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

class DailyProgressDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailyProgressNotification.update(context.applicationContext)
    }
}
