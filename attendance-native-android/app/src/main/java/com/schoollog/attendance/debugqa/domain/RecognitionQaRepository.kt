package com.schoollog.attendance.debugqa.domain

interface RecognitionQaRepository {
    suspend fun clearLocalAttendanceEventsDebugOnly()
    suspend fun clearLocalEmbeddingsForSchoolDebugOnly(schoolId: String)
    fun reloadEmbeddingCacheDebugOnly(): Long
}
