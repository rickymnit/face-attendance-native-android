package com.schoollog.attendance.ml.recognition

data class RecognitionCandidate(
    val studentId: String,
    val confidence: Float,
)
