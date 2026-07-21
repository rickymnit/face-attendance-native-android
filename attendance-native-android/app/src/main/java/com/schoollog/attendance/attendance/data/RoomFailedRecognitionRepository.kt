package com.schoollog.attendance.attendance.data

import com.schoollog.attendance.attendance.data.local.SyncStatusValues
import com.schoollog.attendance.attendance.data.local.dao.FailedRecognitionDao
import com.schoollog.attendance.attendance.data.local.entity.FailedRecognitionEntity
import com.schoollog.attendance.attendance.domain.FailedRecognitionDraft
import com.schoollog.attendance.attendance.domain.FailedRecognitionRepository
import java.util.UUID

class RoomFailedRecognitionRepository(
    private val failedRecognitionDao: FailedRecognitionDao,
) : FailedRecognitionRepository {
    override suspend fun saveFailure(failure: FailedRecognitionDraft) {
        failedRecognitionDao.insert(
            FailedRecognitionEntity(
                id = UUID.randomUUID().toString(),
                schoolId = failure.schoolId,
                deviceId = failure.deviceId,
                reason = failure.reason,
                timestamp = System.currentTimeMillis(),
                qualityScore = failure.qualityScore,
                livenessScore = failure.livenessScore,
                operatorAction = null,
                syncStatus = SyncStatusValues.Pending,
            ),
        )
    }
}
