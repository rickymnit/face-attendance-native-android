package com.schoollog.attendance.attendance.data

import com.schoollog.attendance.attendance.domain.AttendanceRecord

interface AttendanceStore {
    suspend fun save(record: AttendanceRecord)
}
