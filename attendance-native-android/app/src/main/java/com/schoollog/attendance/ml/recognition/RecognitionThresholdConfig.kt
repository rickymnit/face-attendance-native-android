package com.schoollog.attendance.ml.recognition

import com.schoollog.attendance.core.common.RecognitionMode

data class RecognitionThresholds(
    val acceptanceThreshold: Float,
    val ambiguityGap: Float,
)

object RecognitionThresholdConfig {
    fun forMode(mode: RecognitionMode): RecognitionThresholds =
        when (mode) {
            RecognitionMode.Strict -> RecognitionThresholds(
                acceptanceThreshold = 0.86f,
                ambiguityGap = 0.04f,
            )
            RecognitionMode.Balanced -> RecognitionThresholds(
                acceptanceThreshold = 0.80f,
                ambiguityGap = 0.03f,
            )
            RecognitionMode.Lenient -> RecognitionThresholds(
                acceptanceThreshold = 0.74f,
                ambiguityGap = 0.02f,
            )
        }
}
