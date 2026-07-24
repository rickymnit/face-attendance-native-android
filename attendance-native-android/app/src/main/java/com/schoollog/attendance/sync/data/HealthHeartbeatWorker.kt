package com.schoollog.attendance.sync.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.schoollog.attendance.app.SchoollogAttendanceApp

class HealthHeartbeatWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reporter = (applicationContext as SchoollogAttendanceApp).appContainer.deviceHealthReporter
        return if (reporter.sendHealthHeartbeat()) Result.success() else Result.retry()
    }
}
