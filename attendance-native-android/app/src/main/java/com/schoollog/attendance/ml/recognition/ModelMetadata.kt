package com.schoollog.attendance.ml.recognition

import org.tensorflow.lite.DataType

data class ModelMetadata(
    val modelName: String,
    val modelVersion: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val inputDataType: DataType,
    val embeddingSize: Int,
    val normalizationMean: Float,
    val normalizationStd: Float,
    val distanceMetric: DistanceMetric,
) {
    companion object {
        val DefaultFaceEmbedding = ModelMetadata(
            modelName = "facenet_512",
            modelVersion = "multipaz-sample-3f65d0c",
            inputWidth = 160,
            inputHeight = 160,
            inputChannels = 3,
            inputDataType = DataType.FLOAT32,
            embeddingSize = 512,
            normalizationMean = 127.5f,
            normalizationStd = 128f,
            distanceMetric = DistanceMetric.COSINE,
        )
    }
}
