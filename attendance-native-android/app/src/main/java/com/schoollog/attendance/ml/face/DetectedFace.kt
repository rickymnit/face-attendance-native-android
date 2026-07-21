package com.schoollog.attendance.ml.face

data class DetectedFace(
    val trackingId: Int?,
    val boundingBox: FaceBoundingBox,
    val confidence: Float,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
)
