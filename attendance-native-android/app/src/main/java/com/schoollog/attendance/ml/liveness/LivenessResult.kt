package com.schoollog.attendance.ml.liveness

data class LivenessResult(
    val decision: LivenessDecision,
    val score: Float,
    val reason: String,
) {
    val passes: Boolean = decision == LivenessDecision.PASS
}
