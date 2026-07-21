package com.schoollog.attendance.enrollment.domain

import kotlinx.coroutines.flow.Flow

interface EnrollmentRepository {
    fun observeStudents(schoolId: String): Flow<List<EnrollmentProfile>>
    suspend fun searchStudent(schoolId: String, studentId: String): EnrollmentProfile?
    suspend fun saveEnrollment(schoolId: String, form: EnrollmentForm): EnrollmentResult
    suspend fun hasActiveEmbedding(
        schoolId: String,
        studentId: String,
        modelVersion: String,
    ): Boolean
    suspend fun saveLiveEnrollment(
        schoolId: String,
        form: EnrollmentForm,
        embedding: FloatArray,
        modelVersion: String,
        qualityScore: Float,
        allowReEnrollment: Boolean,
    ): LiveEnrollmentResult
}
