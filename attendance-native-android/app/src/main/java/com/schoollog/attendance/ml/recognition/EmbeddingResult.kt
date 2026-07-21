package com.schoollog.attendance.ml.recognition

data class EmbeddingResult(
    val embedding: FloatArray,
    val sourceFrameTimestampNanos: Long,
    val modelVersion: String,
    val failureReason: EmbeddingFailureReason,
    val metadata: ModelMetadata,
) {
    val isSuccess: Boolean = failureReason == EmbeddingFailureReason.SUCCESS
}
