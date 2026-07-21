package com.schoollog.attendance.ml.recognition

import com.schoollog.attendance.ml.face.FaceCropResult

class PlaceholderFaceEmbeddingEngine : FaceEmbeddingEngine {
    override fun generateEmbedding(
        faceCropResult: FaceCropResult,
        sourceFrameTimestampNanos: Long,
    ): EmbeddingResult {
        val vector = floatArrayOf(0.11f, 0.24f, 0.37f, 0.48f, 0.59f, 0.63f, 0.72f, 0.81f)
        return EmbeddingResult(
            embedding = vector,
            sourceFrameTimestampNanos = sourceFrameTimestampNanos,
            modelVersion = "debug-placeholder-embedding-v0",
            failureReason = EmbeddingFailureReason.SUCCESS,
            metadata = ModelMetadata.DefaultFaceEmbedding.copy(
                modelName = "debug_placeholder_embedding",
                modelVersion = "debug-placeholder-embedding-v0",
                embeddingSize = vector.size,
            ),
        )
    }
}
