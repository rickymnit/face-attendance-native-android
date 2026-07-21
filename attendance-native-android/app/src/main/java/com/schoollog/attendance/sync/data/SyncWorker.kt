package com.schoollog.attendance.sync.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.sync.domain.SyncRunStatus

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as SchoollogAttendanceApp).appContainer
        return when (appContainer.attendanceSyncRepository.syncPendingAttendanceEvents()) {
            is SyncRunStatus.Success -> Result.success()
            is SyncRunStatus.Failed -> Result.retry()
            SyncRunStatus.Idle,
            SyncRunStatus.Running -> Result.retry()
        }
    }
}
