package com.schoollog.attendance.app.setup

data class DeviceSetupUiState(
    val schoolCode: String = "",
    val gateId: String = "",
    val deviceName: String = android.os.Build.MODEL.orEmpty(),
    val setupToken: String = "",
    val isRegistering: Boolean = false,
    val statusMessage: String? = null,
)
