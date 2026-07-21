package com.schoollog.attendance.ml.face

data class FaceDetectionResult(
    val faces: List<DetectedFace>,
    val quality: FaceQualityResult,
) {
    val faceCount: Int = faces.size
    val hasExactlyOneFace: Boolean = faceCount == 1
    val primaryFace: DetectedFace? = faces.firstOrNull()
}
