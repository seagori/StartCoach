package com.seagori.hgr

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.seagori.hgr.di.AppContainer
import com.seagori.hgr.reminder.DailyProgressNotification
import com.seagori.hgr.reminder.ReminderManager

class StartCoachApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderManager.ensureInitialized(this, container.settings)
        DailyProgressNotification.update(this, container.settings)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                ReminderManager.maybeShow(this@StartCoachApplication, container.settings)
            }

            override fun onStop(owner: LifecycleOwner) {
                container.syncManager.stopDiscovery()
            }
        })
    }
}
