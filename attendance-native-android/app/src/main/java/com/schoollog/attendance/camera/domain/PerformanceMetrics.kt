package com.schoollog.attendance.camera.domain

data class PerformanceMetrics(
    val analyzerInputFps: Double = 0.0,
    val processedFps: Double = 0.0,
    val averageFaceDetectionMillis: Double = 0.0,
    val averageQualityEvaluationMillis: Double = 0.0,
    val averageStableTrackingMillis: Double = 0.0,
    val averageLivenessMillis: Double = 0.0,
    val averageFaceCropMillis: Double = 0.0,
    val averageEmbeddingInferenceMillis: Double = 0.0,
    val averageMatchingMillis: Double = 0.0,
    val averagePipelineDecisionMillis: Double = 0.0,
    val lastTopMatchScores: List<Float> = emptyList(),
    val droppedFrameCount: Long = 0L,
    val memoryUsedMb: Double = 0.0,
    val lastFailureReason: String = "None",
)
