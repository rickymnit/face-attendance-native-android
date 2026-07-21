package com.schoollog.attendance.attendance.data

import com.schoollog.attendance.attendance.data.local.dao.StudentDao
import com.schoollog.attendance.attendance.data.local.entity.StudentEntity
import com.schoollog.attendance.attendance.domain.StudentAttendanceResult
import com.schoollog.attendance.attendance.domain.StudentRepository

class RoomStudentRepository(
    private val studentDao: StudentDao,
) : StudentRepository {
    override suspend fun findByErpStudentId(
        schoolId: String,
        erpStudentId: String,
    ): StudentAttendanceResult? =
        studentDao.findByErpStudentId(schoolId, erpStudentId)?.let { student ->
            StudentAttendanceResult(
                studentId = student.erpStudentId,
                name = student.name,
                className = student.className,
                section = student.section,
                rollNumber = student.rollNumber,
            )
        }

    override suspend fun upsertRemoteStudent(
        schoolId: String,
        erpStudentId: String,
        name: String,
        className: String,
        section: String,
        rollNumber: String,
        status: String,
        updatedAt: Long,
    ) {
        val existing = studentDao.findByErpStudentId(schoolId, erpStudentId)
        studentDao.upsert(
            StudentEntity(
                localId = existing?.localId ?: 0,
                schoolId = schoolId,
                erpStudentId = erpStudentId,
                name = name,
                className = className,
                section = section,
                rollNumber = rollNumber,
                status = status,
                updatedAt = updatedAt,
            ),
        )
    }
}
