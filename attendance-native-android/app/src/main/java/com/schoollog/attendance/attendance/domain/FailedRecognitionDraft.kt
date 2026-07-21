package com.schoollog.attendance.attendance.domain

data class FailedRecognitionDraft(
    val schoolId: String,
    val deviceId: String,
    val reason: String,
    val qualityScore: Float,
    val livenessScore: Float,
)
