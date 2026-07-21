package com.schoollog.attendance.app

import com.schoollog.attendance.core.common.RecognitionMode

data class SettingsUiState(
    val schoolId: String = "",
    val schoolName: String? = null,
    val deviceId: String = "",
    val gateId: String = "",
    val deviceName: String = "",
    val configVersion: Long = 0L,
    val embeddingSyncVersion: Long = 0L,
    val lastHeartbeatAtMillis: Long? = null,
    val schoolStartTime: String = "",
    val lateAfterTime: String = "",
    val halfDayAfterTime: String = "",
    val duplicateScanCooldownMinutes: String = "",
    val requireOutTime: Boolean = false,
    val recognitionMode: RecognitionMode = RecognitionMode.Strict,
    val showDebugMetricsPanel: Boolean = false,
    val allowDebugMockRecognition: Boolean = false,
    val statusMessage: String? = null,
)
