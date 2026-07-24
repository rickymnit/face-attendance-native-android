package com.schoollog.attendance.core.common

data class DeviceBinding(
    val schoolId: String,
    val schoolCode: String,
    val schoolName: String?,
    val deviceId: String,
    val gateId: String,
    val deviceName: String,
    val authToken: String,
    val configVersion: Long,
    val embeddingSyncVersion: Long,
    val registeredAtMillis: Long,
    val lastHeartbeatAtMillis: Long?,
    val lastAttendanceSyncAtMillis: Long?,
    val lastEmbeddingSyncAtMillis: Long?,
)
