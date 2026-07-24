package com.schoollog.attendance.sync.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AttendanceSyncScheduler {
    private const val PeriodicWorkName = "attendance-periodic-sync"
    private const val ManualWorkName = "attendance-manual-sync"
    private const val PeriodicEmbeddingWorkName = "embedding-periodic-sync"
    private const val ManualEmbeddingWorkName = "embedding-manual-sync"
    private const val PeriodicHealthWorkName = "device-health-periodic-heartbeat"

    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun schedulePeriodicHealthHeartbeat(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthHeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicHealthWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun schedulePeriodicEmbeddingSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<EmbeddingSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicEmbeddingWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ManualWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun syncEmbeddingsNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<EmbeddingSyncWorker>()
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ManualEmbeddingWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun syncConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
