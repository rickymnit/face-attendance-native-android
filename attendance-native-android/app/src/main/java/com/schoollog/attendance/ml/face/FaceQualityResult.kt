package com.schoollog.attendance.ml.face

data class FaceQualityResult(
    val qualityPassed: Boolean,
    val qualityScore: Float,
    val failureReason: FaceQualityFailureReason,
) {
    val passes: Boolean = qualityPassed
    val score: Float = qualityScore
    val reason: String = failureReason.displayText
}
