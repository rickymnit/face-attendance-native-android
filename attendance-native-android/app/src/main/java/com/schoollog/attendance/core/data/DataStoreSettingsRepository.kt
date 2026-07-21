package com.schoollog.attendance.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.core.common.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "attendance_rules")

class DataStoreSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val attendanceRules: Flow<AttendanceRules> =
        dataStore.data.map { preferences ->
            AttendanceRules(
                schoolId = preferences[SchoolIdKey] ?: AttendanceRules.DefaultSchoolId,
                deviceId = preferences[DeviceIdKey] ?: AttendanceRules.DefaultDeviceId,
                gateId = preferences[GateIdKey] ?: AttendanceRules.DefaultGateId,
                schoolStartTime = preferences[SchoolStartTimeKey] ?: "08:00",
                lateAfterTime = preferences[LateAfterTimeKey] ?: "08:15",
                halfDayAfterTime = preferences[HalfDayAfterTimeKey] ?: "10:30",
                duplicateScanCooldownMinutes = preferences[DuplicateCooldownMinutesKey] ?: 10,
                requireOutTime = preferences[RequireOutTimeKey] ?: false,
                recognitionMode = preferences[RecognitionModeKey]?.let { storedMode ->
                    runCatching { RecognitionMode.valueOf(storedMode) }.getOrDefault(RecognitionMode.Strict)
                } ?: RecognitionMode.Strict,
                showDebugMetricsPanel = preferences[ShowDebugMetricsPanelKey] ?: false,
                allowDebugMockRecognition = preferences[AllowDebugMockRecognitionKey] ?: false,
            )
        }

    override suspend fun saveAttendanceRules(attendanceRules: AttendanceRules) {
        dataStore.edit { preferences ->
            preferences[SchoolIdKey] = attendanceRules.schoolId.trim()
            preferences[DeviceIdKey] = attendanceRules.deviceId.trim()
            preferences[GateIdKey] = attendanceRules.gateId.trim()
            preferences[SchoolStartTimeKey] = attendanceRules.schoolStartTime.trim()
            preferences[LateAfterTimeKey] = attendanceRules.lateAfterTime.trim()
            preferences[HalfDayAfterTimeKey] = attendanceRules.halfDayAfterTime.trim()
            preferences[DuplicateCooldownMinutesKey] = attendanceRules.duplicateScanCooldownMinutes.coerceAtLeast(1)
            preferences[RequireOutTimeKey] = attendanceRules.requireOutTime
            preferences[RecognitionModeKey] = attendanceRules.recognitionMode.name
            preferences[ShowDebugMetricsPanelKey] = attendanceRules.showDebugMetricsPanel
            preferences[AllowDebugMockRecognitionKey] = attendanceRules.allowDebugMockRecognition
        }
    }

    private companion object {
        val SchoolIdKey = stringPreferencesKey("school_id")
        val DeviceIdKey = stringPreferencesKey("device_id")
        val GateIdKey = stringPreferencesKey("gate_id")
        val SchoolStartTimeKey = stringPreferencesKey("school_start_time")
        val LateAfterTimeKey = stringPreferencesKey("late_after_time")
        val HalfDayAfterTimeKey = stringPreferencesKey("half_day_after_time")
        val DuplicateCooldownMinutesKey = intPreferencesKey("duplicate_cooldown_minutes")
        val RequireOutTimeKey = booleanPreferencesKey("require_out_time")
        val RecognitionModeKey = stringPreferencesKey("recognition_mode")
        val ShowDebugMetricsPanelKey = booleanPreferencesKey("show_debug_metrics_panel")
        val AllowDebugMockRecognitionKey = booleanPreferencesKey("allow_debug_mock_recognition")
    }
}
