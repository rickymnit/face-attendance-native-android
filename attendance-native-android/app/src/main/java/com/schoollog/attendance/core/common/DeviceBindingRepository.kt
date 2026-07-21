package com.schoollog.attendance.core.common

import com.schoollog.attendance.sync.data.SyncAuthProvider
import kotlinx.coroutines.flow.Flow

interface DeviceBindingRepository : SyncAuthProvider {
    val deviceBinding: Flow<DeviceBinding?>
    fun currentBinding(): DeviceBinding?
    suspend fun saveBinding(binding: DeviceBinding)
    suspend fun updateLastHeartbeat(timestampMillis: Long)
    suspend fun updateEmbeddingSyncVersion(version: Long)
    suspend fun clearBinding()
}
