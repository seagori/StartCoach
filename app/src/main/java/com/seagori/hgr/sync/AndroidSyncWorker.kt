package com.seagori.hgr.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seagori.hgr.StartCoachApplication

class AndroidSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? StartCoachApplication ?: return Result.failure()
        val manager = app.container.syncManager
        if (!manager.isPaired()) return Result.success()
        return manager.syncNowInternal().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
