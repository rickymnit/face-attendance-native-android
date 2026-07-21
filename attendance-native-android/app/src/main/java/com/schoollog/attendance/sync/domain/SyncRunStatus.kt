package com.schoollog.attendance.sync.domain

sealed interface SyncRunStatus {
    data object Idle : SyncRunStatus
    data object Running : SyncRunStatus
    data class Success(val syncedCount: Int, val completedAtMillis: Long, val duplicateCount: Int = 0) : SyncRunStatus
    data class Failed(val message: String, val completedAtMillis: Long) : SyncRunStatus
}
