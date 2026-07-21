package com.schoollog.attendance.enrollment.domain

sealed interface EnrollmentResult {
    data class Saved(val debugPlaceholderEmbeddingCreated: Boolean) : EnrollmentResult
    data class ValidationError(val message: String) : EnrollmentResult
    data class DuplicateStudent(val studentId: String) : EnrollmentResult
}
