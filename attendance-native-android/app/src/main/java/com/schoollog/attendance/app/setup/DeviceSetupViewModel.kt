package com.schoollog.attendance.app.setup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.DeviceBinding
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.sync.data.DeviceInfoRequest
import com.schoollog.attendance.sync.data.DeviceRegistrationRequest
import com.schoollog.attendance.sync.data.DeviceRegistrationResponse
import com.schoollog.attendance.sync.data.SyncApi
import com.schoollog.attendance.sync.data.SyncApiResult
import com.schoollog.attendance.sync.domain.EmbeddingSyncRepository
import com.schoollog.attendance.sync.domain.SyncRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceSetupViewModel(
    private val syncApi: SyncApi,
    private val deviceBindingRepository: DeviceBindingRepository,
    private val settingsRepository: SettingsRepository,
    private val embeddingSyncRepository: EmbeddingSyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceSetupUiState())
    val uiState: StateFlow<DeviceSetupUiState> = _uiState.asStateFlow()

    fun onSchoolCodeChanged(value: String) = update { copy(schoolCode = value.trim()) }
    fun onGateIdChanged(value: String) = update { copy(gateId = value.trim()) }
    fun onDeviceNameChanged(value: String) = update { copy(deviceName = value) }
    fun onSetupTokenChanged(value: String) = update { copy(setupToken = value.trim()) }

    fun registerDevice() {
        val current = _uiState.value
        if (current.schoolCode.isBlank() || current.gateId.isBlank() || current.setupToken.isBlank()) {
            _uiState.update { it.copy(statusMessage = "School code, gate ID, and setup token are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, statusMessage = "Registering device...") }
            val request = DeviceRegistrationRequest(
                schoolCode = current.schoolCode,
                gateId = current.gateId,
                deviceName = current.deviceName.ifBlank { Build.MODEL.orEmpty() },
                setupToken = current.setupToken,
                deviceInfo = DeviceInfoRequest(
                    manufacturer = Build.MANUFACTURER.orEmpty(),
                    model = Build.MODEL.orEmpty(),
                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
            when (val result = syncApi.registerDevice(request)) {
                is SyncApiResult.Success -> saveRegistration(request, result.data)
                is SyncApiResult.Error -> _uiState.update {
                    it.copy(
                        isRegistering = false,
                        statusMessage = "Registration failed: ${result.message}",
                    )
                }
            }
        }
    }

    private suspend fun saveRegistration(
        request: DeviceRegistrationRequest,
        response: DeviceRegistrationResponse,
    ) {
        val now = System.currentTimeMillis()
        deviceBindingRepository.saveBinding(
            DeviceBinding(
                schoolId = response.schoolId,
                schoolCode = response.schoolCode ?: request.schoolCode,
                schoolName = response.schoolName,
                deviceId = response.deviceId,
                gateId = response.gateId,
                deviceName = request.deviceName,
                authToken = response.deviceAccessToken,
                configVersion = response.configVersion,
                embeddingSyncVersion = response.embeddingSyncVersion,
                registeredAtMillis = now,
                lastHeartbeatAtMillis = null,
                lastAttendanceSyncAtMillis = null,
                lastEmbeddingSyncAtMillis = null,
            ),
        )
        settingsRepository.saveAttendanceRules(response.toAttendanceRules(request))
        _uiState.update { it.copy(statusMessage = "Device registered. Syncing embeddings...") }
        val syncStatus = embeddingSyncRepository.syncEmbeddingDelta()
        val message = when (syncStatus) {
            is SyncRunStatus.Success -> "Device registered. Synced ${syncStatus.syncedCount} embedding change(s)."
            is SyncRunStatus.Failed -> "Device registered. Embedding sync failed: ${syncStatus.message}"
            SyncRunStatus.Idle,
            SyncRunStatus.Running -> "Device registered"
        }
        _uiState.update { it.copy(isRegistering = false, statusMessage = message) }
    }

    private fun DeviceRegistrationResponse.toAttendanceRules(request: DeviceRegistrationRequest): AttendanceRules {
        val remoteRules = config?.attendanceRules
        return AttendanceRules(
            schoolId = schoolId,
            deviceId = deviceId,
            gateId = gateId.ifBlank { request.gateId },
            schoolStartTime = remoteRules?.schoolStartTime ?: "08:00",
            lateAfterTime = remoteRules?.lateAfterTime ?: "08:15",
            halfDayAfterTime = remoteRules?.halfDayAfterTime ?: "10:30",
            duplicateScanCooldownMinutes = remoteRules?.duplicateScanCooldownMinutes ?: 10,
            requireOutTime = remoteRules?.requireOutTime ?: false,
            recognitionMode = remoteRules?.recognitionMode?.let { mode ->
                runCatching { RecognitionMode.valueOf(mode) }.getOrDefault(RecognitionMode.Strict)
            } ?: RecognitionMode.Strict,
        )
    }

    private fun update(block: DeviceSetupUiState.() -> DeviceSetupUiState) {
        _uiState.update { it.block().copy(statusMessage = null) }
    }

    companion object {
        fun factory(
            syncApi: SyncApi,
            deviceBindingRepository: DeviceBindingRepository,
            settingsRepository: SettingsRepository,
            embeddingSyncRepository: EmbeddingSyncRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DeviceSetupViewModel(syncApi, deviceBindingRepository, settingsRepository, embeddingSyncRepository) as T
        }
    }
}
