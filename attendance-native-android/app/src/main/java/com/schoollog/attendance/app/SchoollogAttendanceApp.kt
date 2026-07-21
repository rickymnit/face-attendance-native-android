package com.schoollog.attendance.app

import android.app.Application
import com.schoollog.attendance.sync.data.AttendanceSyncScheduler

class SchoollogAttendanceApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        AttendanceSyncScheduler.schedulePeriodicSync(this)
        AttendanceSyncScheduler.schedulePeriodicEmbeddingSync(this)
    }
}
