package com.schoollog.attendance.ml.recognition

import com.schoollog.attendance.core.common.RecognitionMode

data class FaceMatchRequest(
    val schoolId: String,
    val recognitionMode: RecognitionMode,
    val embedding: EmbeddingResult,
)
