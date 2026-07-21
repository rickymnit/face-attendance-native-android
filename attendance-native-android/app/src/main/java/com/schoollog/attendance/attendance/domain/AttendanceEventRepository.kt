package com.schoollog.attendance.attendance.domain

import kotlinx.coroutines.flow.Flow

interface AttendanceEventRepository {
    val pendingSyncCount: Flow<Int>
    suspend fun saveAttendanceEvent(event: AttendanceEventDraft)
}
