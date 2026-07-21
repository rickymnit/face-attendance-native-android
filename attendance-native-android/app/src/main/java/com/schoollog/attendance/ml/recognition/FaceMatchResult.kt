package com.schoollog.attendance.ml.recognition

data class FaceMatchCandidate(
    val studentId: String,
    val score: Float,
)

data class FaceMatchResult(
    val bestStudentId: String?,
    val bestScore: Float,
    val secondBestScore: Float?,
    val decision: RecognitionDecision,
    val reason: String,
    val topMatches: List<FaceMatchCandidate>,
    val matchingTimeMillis: Double,
) {
    val studentId: String? get() = bestStudentId
    val score: Float get() = bestScore
}
