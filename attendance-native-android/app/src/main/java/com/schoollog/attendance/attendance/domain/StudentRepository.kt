package com.schoollog.attendance.attendance.domain

interface StudentRepository {
    suspend fun findByErpStudentId(schoolId: String, erpStudentId: String): StudentAttendanceResult?
    suspend fun upsertRemoteStudent(
        schoolId: String,
        erpStudentId: String,
        name: String,
        className: String,
        section: String,
        rollNumber: String,
        status: String,
        updatedAt: Long,
    )
}
