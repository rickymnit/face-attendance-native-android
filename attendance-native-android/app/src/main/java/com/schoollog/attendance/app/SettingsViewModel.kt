package com.schoollog.attendance.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.DeviceBinding
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.core.common.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val deviceBindingRepository: DeviceBindingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var deviceBinding: DeviceBinding? = null

    init {
        viewModelScope.launch {
            settingsRepository.attendanceRules.collect { rules ->
                _uiState.value = rules.toUiState(deviceBinding, _uiState.value.statusMessage)
            }
        }
        viewModelScope.launch {
            deviceBindingRepository.deviceBinding.collect { binding ->
                deviceBinding = binding
                _uiState.update { it.withBinding(binding) }
            }
        }
    }

    fun onSchoolStartTimeChanged(value: String) = update { copy(schoolStartTime = value) }
    fun onLateAfterTimeChanged(value: String) = update { copy(lateAfterTime = value) }
    fun onHalfDayAfterTimeChanged(value: String) = update { copy(halfDayAfterTime = value) }
    fun onDuplicateCooldownChanged(value: String) = update { copy(duplicateScanCooldownMinutes = value.filter { it.isDigit() }) }
    fun onRequireOutTimeChanged(value: Boolean) = update { copy(requireOutTime = value) }
    fun onRecognitionModeChanged(value: RecognitionMode) = update { copy(recognitionMode = value) }
    fun onShowDebugMetricsPanelChanged(value: Boolean) = update { copy(showDebugMetricsPanel = value) }
    fun onAllowDebugMockRecognitionChanged(value: Boolean) = update { copy(allowDebugMockRecognition = value) }

    fun saveSettings() {
        val current = _uiState.value
        val cooldownMinutes = current.duplicateScanCooldownMinutes.toIntOrNull()
        val binding = deviceBindingRepository.currentBinding()
        if (binding == null) {
            _uiState.update { it.copy(statusMessage = "Device is not registered") }
            return
        }
        if (cooldownMinutes == null || cooldownMinutes < 1) {
            _uiState.update { it.copy(statusMessage = "Cooldown must be at least 1 minute") }
            return
        }

        viewModelScope.launch {
            settingsRepository.saveAttendanceRules(
                AttendanceRules(
                    schoolId = binding.schoolId,
                    deviceId = binding.deviceId,
                    gateId = binding.gateId,
                    schoolStartTime = current.schoolStartTime,
                    lateAfterTime = current.lateAfterTime,
                    halfDayAfterTime = current.halfDayAfterTime,
                    duplicateScanCooldownMinutes = cooldownMinutes,
                    requireOutTime = current.requireOutTime,
                    recognitionMode = current.recognitionMode,
                    showDebugMetricsPanel = current.showDebugMetricsPanel,
                    allowDebugMockRecognition = current.allowDebugMockRecognition,
                ),
            )
            _uiState.update { it.copy(statusMessage = "Settings saved") }
        }
    }


    fun unbindDeviceDebugOnly() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            deviceBindingRepository.clearBinding()
        }
    }

    private fun update(block: SettingsUiState.() -> SettingsUiState) {
        _uiState.update { it.block().copy(statusMessage = null) }
    }

    private fun AttendanceRules.toUiState(
        binding: DeviceBinding?,
        statusMessage: String?,
    ): SettingsUiState =
        SettingsUiState(
            schoolId = binding?.schoolId ?: schoolId,
            schoolName = binding?.schoolName,
            deviceId = binding?.deviceId ?: deviceId,
            gateId = binding?.gateId ?: gateId,
            deviceName = binding?.deviceName.orEmpty(),
            configVersion = binding?.configVersion ?: 0L,
            embeddingSyncVersion = binding?.embeddingSyncVersion ?: 0L,
            lastHeartbeatAtMillis = binding?.lastHeartbeatAtMillis,
            schoolStartTime = schoolStartTime,
            lateAfterTime = lateAfterTime,
            halfDayAfterTime = halfDayAfterTime,
            duplicateScanCooldownMinutes = duplicateScanCooldownMinutes.toString(),
            requireOutTime = requireOutTime,
            recognitionMode = recognitionMode,
            showDebugMetricsPanel = showDebugMetricsPanel,
            allowDebugMockRecognition = allowDebugMockRecognition,
            statusMessage = statusMessage,
        )


    private fun SettingsUiState.withBinding(binding: DeviceBinding?): SettingsUiState =
        copy(
            schoolId = binding?.schoolId ?: schoolId,
            schoolName = binding?.schoolName,
            deviceId = binding?.deviceId ?: deviceId,
            gateId = binding?.gateId ?: gateId,
            deviceName = binding?.deviceName.orEmpty(),
            configVersion = binding?.configVersion ?: configVersion,
            embeddingSyncVersion = binding?.embeddingSyncVersion ?: embeddingSyncVersion,
            lastHeartbeatAtMillis = binding?.lastHeartbeatAtMillis,
        )

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            deviceBindingRepository: DeviceBindingRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(settingsRepository, deviceBindingRepository) as T
            }
    }
}
