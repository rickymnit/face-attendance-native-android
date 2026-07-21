package com.schoollog.attendance.camera.domain

data class BenchmarkMetricSummary(
    val average: Double = 0.0,
    val p95: Double = 0.0,
    val worst: Double = 0.0,
)

data class PipelineBenchmarkResult(
    val sessionId: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val durationMillis: Long,
    val deviceModel: String,
    val androidVersion: String,
    val analyzerInputFps: BenchmarkMetricSummary,
    val processedFps: BenchmarkMetricSummary,
    val faceDetectionMillis: BenchmarkMetricSummary,
    val faceQualityMillis: BenchmarkMetricSummary,
    val stableTrackingMillis: BenchmarkMetricSummary,
    val livenessMillis: BenchmarkMetricSummary,
    val faceCropMillis: BenchmarkMetricSummary,
    val embeddingInferenceMillis: BenchmarkMetricSummary,
    val localMatchingMillis: BenchmarkMetricSummary,
    val totalDecisionMillis: BenchmarkMetricSummary,
    val memoryUsedMb: BenchmarkMetricSummary,
    val droppedFrames: Long,
    val successCount: Int,
    val failureCount: Int,
    val noAnalyzerBacklog: Boolean,
) {
    val targetMet: Boolean = totalDecisionMillis.worst <= TargetDecisionMillis

    companion object {
        const val TargetDecisionMillis = 3_000.0
    }
}

data class PipelineBenchmarkState(
    val isRunning: Boolean = false,
    val sessionId: String? = null,
    val startedAtMillis: Long? = null,
    val durationMillis: Long = 30_000L,
    val result: PipelineBenchmarkResult? = null,
    val statusMessage: String = "Benchmark not run",
)
