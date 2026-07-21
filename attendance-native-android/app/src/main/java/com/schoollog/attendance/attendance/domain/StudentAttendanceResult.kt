package com.schoollog.attendance.attendance.domain

data class StudentAttendanceResult(
    val studentId: String,
    val name: String,
    val className: String,
    val section: String,
    val rollNumber: String,
    val attendanceStatus: String = "",
)
