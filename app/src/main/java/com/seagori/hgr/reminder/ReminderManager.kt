package com.seagori.hgr.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seagori.hgr.MainActivity
import com.seagori.hgr.R
import com.seagori.hgr.StartCoachApplication
import com.seagori.hgr.data.settings.SettingsStore
import com.seagori.hgr.i18n.AppLocale
import java.util.concurrent.TimeUnit

/**
 * 사용자가 화면을 보고 있을 때 설정된 쿨타임마다 조용한 상단 헤드업 배너(IMPORTANCE_HIGH + 무음)를 띄우고
 * 약 4.5초 뒤 자동으로 cancel하여 알림창에서도 사라지게 한다.
 */
object ReminderManager {
    private const val CHANNEL_ID = "startcoach_heads_up_reminder_v2"
    private const val NOTIFICATION_ID = 45871
    private const val DAILY_NOTIFICATION_ID = 45872
    private const val WORK_NAME = "startcoach-heads-up-reminder"
    private const val RETRY_WORK_NAME = "startcoach-heads-up-retry"
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    maybeShow(context.applicationContext)
                }
            }
        }
    }

    fun ensureInitialized(context: Context, settings: SettingsStore) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        if (settings.reminderEnabled || settings.dailyReminderEnabled) {
            registerScreenReceiver(appContext)
            scheduleWorker(appContext)
            scheduleNextBackgroundAlarm(appContext, settings)
        } else {
            cancelWorker(appContext)
            cancelBackgroundAlarm(appContext)
        }
    }

    fun onSettingsChanged(context: Context, settings: SettingsStore) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (!settings.reminderEnabled) {
            manager?.cancel(NOTIFICATION_ID)
        }
        if (!settings.dailyReminderEnabled) {
            manager?.cancel(DAILY_NOTIFICATION_ID)
        }
        if (settings.reminderEnabled || settings.dailyReminderEnabled) {
            registerScreenReceiver(appContext)
            scheduleWorker(appContext)
            maybeShow(appContext, settings)
        } else {
            cancelWorker(appContext)
            cancelBackgroundAlarm(appContext)
        }
    }

    private fun registerScreenReceiver(appContext: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(appContext, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppLocale.strings.reminderSection,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun maybeShow(context: Context, settings: SettingsStore = SettingsStore(context)) {
        if (!settings.reminderEnabled && !settings.dailyReminderEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val now = System.currentTimeMillis()
        if (power?.isInteractive != true || keyguard?.isKeyguardLocked == true) {
            val retryMinutes = minOf(
                if (settings.reminderEnabled) settings.reminderRetryMinutes else Int.MAX_VALUE,
                if (settings.dailyReminderEnabled) settings.dailyReminderRetryMinutes else Int.MAX_VALUE
            ).coerceAtLeast(1)
            if (retryMinutes < Int.MAX_VALUE) {
                scheduleRetry(context, retryMinutes.toLong())
            }
            return
        }
        if (settings.reminderEnabled) {
            val cooldownMs = settings.reminderCooldownMinutes.coerceAtLeast(1) * 60_000L
            if (now - settings.lastReminderAt >= cooldownMs) {
                settings.lastReminderAt = now
                postHeadsUp(
                    context,
                    NOTIFICATION_ID,
                    settings.reminderMessage.ifBlank { AppLocale.strings.reminderDefaultText },
                    settings.reminderDurationSeconds.coerceAtLeast(1) * 1_000L
                )
            }
        }
        if (settings.dailyReminderEnabled) {
            val dailyState = (context.applicationContext as StartCoachApplication).container.dailyTaskStore.state.value
            val hasRemaining = dailyState.tasks.isNotEmpty() && dailyState.todayPercent() < 100
            val dailyCooldownMs = settings.dailyReminderCooldownMinutes.coerceAtLeast(1) * 60_000L
            if (hasRemaining && now - settings.lastDailyReminderAt >= dailyCooldownMs) {
                settings.lastDailyReminderAt = now
                postHeadsUp(
                    context,
                    DAILY_NOTIFICATION_ID,
                    AppLocale.strings.dailyReminderMessage,
                    settings.dailyReminderDurationSeconds.coerceAtLeast(1) * 1_000L
                )
            }
        }
        scheduleNextBackgroundAlarm(context, settings)
    }

    private fun postHeadsUp(
        context: Context,
        notificationId: Int,
        message: String,
        durationMs: Long
    ) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentTitle(AppLocale.strings.appName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(0)
            .setAutoCancel(true)
            .setTimeoutAfter(durationMs)
            .setContentIntent(pending)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun scheduleRetry(context: Context, delayMinutes: Long) {
        val delay = delayMinutes.coerceAtLeast(1L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        scheduleAlarmAt(context, System.currentTimeMillis() + delay * 60_000L)
    }

    fun scheduleNextBackgroundAlarm(context: Context, settings: SettingsStore = SettingsStore(context)) {
        if (!settings.reminderEnabled && !settings.dailyReminderEnabled) {
            cancelBackgroundAlarm(context)
            return
        }
        val now = System.currentTimeMillis()
        var nextAt = Long.MAX_VALUE
        if (settings.reminderEnabled) {
            val due = settings.lastReminderAt + settings.reminderCooldownMinutes.coerceAtLeast(1) * 60_000L
            nextAt = minOf(nextAt, maxOf(due, now + 60_000L))
        }
        if (settings.dailyReminderEnabled) {
            val due = settings.lastDailyReminderAt + settings.dailyReminderCooldownMinutes.coerceAtLeast(1) * 60_000L
            nextAt = minOf(nextAt, maxOf(due, now + 60_000L))
        }
        if (nextAt < Long.MAX_VALUE) {
            scheduleAlarmAt(context, nextAt)
        }
    }

    private fun scheduleAlarmAt(context: Context, triggerAtMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            alarmPendingIntent(context)
        )
    }

    private fun cancelBackgroundAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, ReminderAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleWorker(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(RETRY_WORK_NAME)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val settings = SettingsStore(appContext)
        ReminderManager.maybeShow(appContext, settings)
        ReminderManager.scheduleNextBackgroundAlarm(appContext, settings)
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        ReminderManager.maybeShow(applicationContext, settings)
        ReminderManager.scheduleNextBackgroundAlarm(applicationContext, settings)
        return Result.success()
    }
}
