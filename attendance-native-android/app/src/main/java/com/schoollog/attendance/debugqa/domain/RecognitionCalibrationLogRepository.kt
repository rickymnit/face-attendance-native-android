package com.schoollog.attendance.debugqa.domain

import kotlinx.coroutines.flow.Flow

interface RecognitionCalibrationLogRepository {
    val latestLog: Flow<RecognitionCalibrationLog?>

    suspend fun logAttempt(draft: RecognitionCalibrationLogDraft)
    suspend fun exportCsv(limit: Int = 5_000): String
    suspend fun summary(limit: Int = 5_000): RecognitionCalibrationSummary
    suspend fun clearDebugOnly()
}
