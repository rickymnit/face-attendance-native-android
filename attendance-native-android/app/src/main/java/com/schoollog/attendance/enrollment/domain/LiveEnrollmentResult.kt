package com.schoollog.attendance.enrollment.domain

sealed interface LiveEnrollmentResult {
    data object Saved : LiveEnrollmentResult
    data class ValidationError(val message: String) : LiveEnrollmentResult
    data class ActiveEmbeddingExists(val studentId: String) : LiveEnrollmentResult
}
