package com.schoollog.attendance.sync.data

import com.schoollog.attendance.sync.domain.SyncStatus

interface SyncQueueDataSource {
    suspend fun pendingCount(): Int
    suspend fun updateStatus(id: String, status: SyncStatus)
}
