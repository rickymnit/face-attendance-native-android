package com.schoollog.attendance.core.common

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val attendanceRules: Flow<AttendanceRules>
    suspend fun saveAttendanceRules(attendanceRules: AttendanceRules)
}
