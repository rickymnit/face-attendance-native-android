package com.schoollog.attendance.sync.domain

import kotlinx.coroutines.flow.StateFlow

interface EmbeddingSyncRepository {
    val lastEmbeddingSyncStatus: StateFlow<SyncRunStatus>
    suspend fun syncEmbeddingDelta(): SyncRunStatus
}
