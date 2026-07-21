package com.schoollog.attendance.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "failed_recognitions",
    indices = [Index(value = ["syncStatus", "timestamp"])],
)
data class FailedRecognitionEntity(
    @PrimaryKey val id: String,
    val schoolId: String,
    val deviceId: String,
    val reason: String,
    val timestamp: Long,
    val qualityScore: Float,
    val livenessScore: Float,
    val operatorAction: String?,
    val syncStatus: String,
)
