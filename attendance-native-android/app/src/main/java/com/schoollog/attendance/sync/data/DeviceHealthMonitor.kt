package com.schoollog.attendance.sync.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeviceHealthMonitor {
    private val _cameraStatus = MutableStateFlow("Unknown")
    val cameraStatus: StateFlow<String> = _cameraStatus.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun updateCameraStatus(status: String) {
        _cameraStatus.value = status.take(MaxStatusLength)
    }

    fun updateLastError(error: String?) {
        _lastError.value = error?.take(MaxErrorLength)
    }

    private const val MaxStatusLength = 120
    private const val MaxErrorLength = 240
}
