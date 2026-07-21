package com.schoollog.attendance.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_events")
data class AttendanceEventEntity(
    @PrimaryKey val eventId: String,
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
    val syncStatus: String,
    val createdAt: Long,
)
