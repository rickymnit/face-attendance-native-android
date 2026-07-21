package com.schoollog.attendance.debugqa.data

import com.schoollog.attendance.attendance.data.local.dao.AttendanceEventDao
import com.schoollog.attendance.attendance.data.local.dao.FaceEmbeddingDao
import com.schoollog.attendance.attendance.domain.FaceEmbeddingCacheVersion
import com.schoollog.attendance.debugqa.domain.RecognitionQaRepository

class RoomRecognitionQaRepository(
    private val attendanceEventDao: AttendanceEventDao,
    private val faceEmbeddingDao: FaceEmbeddingDao,
) : RecognitionQaRepository {
    override suspend fun clearLocalAttendanceEventsDebugOnly() {
        attendanceEventDao.clearAllDebugOnly()
    }

    override suspend fun clearLocalEmbeddingsForSchoolDebugOnly(schoolId: String) {
        faceEmbeddingDao.deleteBySchoolDebugOnly(schoolId)
        FaceEmbeddingCacheVersion.markChanged()
    }

    override fun reloadEmbeddingCacheDebugOnly(): Long =
        FaceEmbeddingCacheVersion.markChanged()
}
