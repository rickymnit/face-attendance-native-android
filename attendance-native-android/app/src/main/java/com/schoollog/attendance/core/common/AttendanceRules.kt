package com.schoollog.attendance.core.common

data class AttendanceRules(
    val schoolId: String = DefaultSchoolId,
    val deviceId: String = DefaultDeviceId,
    val gateId: String = DefaultGateId,
    val schoolStartTime: String = "08:00",
    val lateAfterTime: String = "08:15",
    val halfDayAfterTime: String = "10:30",
    val duplicateScanCooldownMinutes: Int = 10,
    val requireOutTime: Boolean = false,
    val recognitionMode: RecognitionMode = RecognitionMode.Strict,
    val showDebugMetricsPanel: Boolean = false,
    val allowDebugMockRecognition: Boolean = false,
) {
    companion object {
        const val DefaultSchoolId = "SCHOOL_PLACEHOLDER"
        const val DefaultDeviceId = "ANDROID_GATE_DEVICE_PLACEHOLDER"
        const val DefaultGateId = "MAIN_GATE"
    }
}
