package com.schoollog.attendance.debugqa.domain

import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.ml.recognition.RecognitionDecision

enum class CalibrationLightingCondition {
    NORMAL,
    LOW_LIGHT,
    BRIGHT_LIGHT,
    OUTDOOR,
    INDOOR,
}

enum class CalibrationFaceCondition {
    NORMAL,
    GLASSES,
    MASK,
    HAIR_CHANGE,
    SIDE_ANGLE,
    FAST_MOVEMENT,
}

data class RecognitionCalibrationSummary(
    val genuineAccepted: Int = 0,
    val genuineRejected: Int = 0,
    val wrongAccepted: Int = 0,
    val ambiguousRejected: Int = 0,
    val lowConfidenceRejected: Int = 0,
    val averageDecisionTimeMs: Double = 0.0,
)

data class RecognitionCalibrationLog(
    val id: String,
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

data class RecognitionCalibrationLogDraft(
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
    val decision: RecognitionDecision,
    val failureReason: String?,
    val recognitionMode: RecognitionMode,
    val inferenceTimeMs: Double,
    val matchingTimeMs: Double,
    val totalDecisionTimeMs: Double,
    val lightingCondition: CalibrationLightingCondition,
    val faceCondition: CalibrationFaceCondition,
    val testerName: String?,
    val deviceModel: String,
    val androidVersion: String,
    val modelVersion: String,
    val schoolId: String,
    val sessionNotes: String?,
)
