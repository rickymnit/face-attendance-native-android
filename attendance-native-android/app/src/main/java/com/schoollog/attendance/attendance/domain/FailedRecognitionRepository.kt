package com.schoollog.attendance.attendance.domain

interface FailedRecognitionRepository {
    suspend fun saveFailure(failure: FailedRecognitionDraft)
}
