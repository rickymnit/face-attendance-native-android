package com.schoollog.attendance.debugqa.domain

import com.schoollog.attendance.ml.recognition.ModelMetadata

data class ModelSmokeTestResult(
    val modelFound: Boolean,
    val modelLoaded: Boolean,
    val success: Boolean,
    val metadata: ModelMetadata,
    val averageInferenceTimeMillis: Double?,
    val inputShape: String?,
    val inputDataType: String?,
    val outputShape: String?,
    val outputDataType: String?,
    val error: String?,
)
