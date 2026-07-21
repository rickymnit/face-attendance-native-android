package com.schoollog.attendance.ml.recognition

data class EnrolledFaceEmbedding(
    val studentId: String,
    val vector: FloatArray,
)
