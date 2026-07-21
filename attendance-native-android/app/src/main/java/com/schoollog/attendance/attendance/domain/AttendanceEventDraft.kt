package com.schoollog.attendance.attendance.domain

data class AttendanceEventDraft(
    val schoolId: String,
    val erpStudentId: String,
    val deviceId: String,
    val gateId: String,
    val eventType: String,
    val attendanceDate: String,
    val timestampLocal: Long,
    val matchScore: Float,
    val livenessScore: Float,
    val qualityScore: Float,
)
