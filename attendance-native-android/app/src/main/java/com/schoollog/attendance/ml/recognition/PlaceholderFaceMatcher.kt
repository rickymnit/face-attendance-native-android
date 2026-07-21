package com.schoollog.attendance.ml.recognition

class PlaceholderFaceMatcher : FaceMatcher {
    override fun match(request: FaceMatchRequest): FaceMatchResult {
        if (!request.embedding.isSuccess) {
            return FaceMatchResult(
                bestStudentId = null,
                bestScore = 0f,
                secondBestScore = null,
                decision = RecognitionDecision.NO_MATCH,
                reason = "Debug embedding unavailable",
                topMatches = emptyList(),
                matchingTimeMillis = 0.0,
            )
        }

        val score = 0.91f
        val studentId = "STUDENT_PLACEHOLDER_001"
        return FaceMatchResult(
            bestStudentId = studentId,
            bestScore = score,
            secondBestScore = null,
            decision = if (score >= MatchThreshold) {
                RecognitionDecision.MATCH_ACCEPTED
            } else {
                RecognitionDecision.LOW_CONFIDENCE
            },
            reason = "Debug mock matcher result",
            topMatches = listOf(FaceMatchCandidate(studentId, score)),
            matchingTimeMillis = 0.0,
        )
    }

    private companion object {
        const val MatchThreshold = 0.80f
    }
}
