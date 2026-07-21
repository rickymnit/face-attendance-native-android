package com.schoollog.attendance.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recognition_calibration_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["sessionId"])],
)
data class RecognitionCalibrationLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val expectedStudentId: String?,
    val predictedStudentId: String?,
    val top1StudentId: String?,
    val top1Score: Float,
    val top2StudentId: String?,
    val top2Score: Float?,
    val top3StudentId: String?,
    val top3Score: Float?,
    val margin: Float?,
    val livenessScore: Float,
    val qualityScore: Float,
    val decision: String,
    val failureReason: String?,
    val recognitionMode: String,
    val inferenceTimeMs: Double,
    val matchingTimeMs: Double,
    val totalDecisionTimeMs: Double,
    val lightingCondition: String,
    val faceCondition: String,
    val testerName: String?,
    val deviceModel: String,
    val androidVersion: String,
    val modelVersion: String,
    val schoolId: String,
    val sessionNotes: String?,
)
