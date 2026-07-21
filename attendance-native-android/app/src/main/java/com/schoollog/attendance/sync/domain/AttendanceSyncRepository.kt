package com.schoollog.attendance.sync.domain

import kotlinx.coroutines.flow.StateFlow

interface AttendanceSyncRepository {
    val lastSyncStatus: StateFlow<SyncRunStatus>
    suspend fun syncPendingAttendanceEvents(): SyncRunStatus
}
