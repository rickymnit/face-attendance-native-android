package com.schoollog.attendance.ml.recognition

import com.schoollog.attendance.ml.face.FaceCropResult

interface FaceEmbeddingEngine : AutoCloseable {
    fun generateEmbedding(
        faceCropResult: FaceCropResult,
        sourceFrameTimestampNanos: Long,
    ): EmbeddingResult

    override fun close() = Unit
}
