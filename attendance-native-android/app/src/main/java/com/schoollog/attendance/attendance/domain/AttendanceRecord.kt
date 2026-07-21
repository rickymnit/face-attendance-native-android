package com.schoollog.attendance.attendance.domain

data class AttendanceRecord(
    val studentId: String,
    val markedAtMillis: Long,
)
