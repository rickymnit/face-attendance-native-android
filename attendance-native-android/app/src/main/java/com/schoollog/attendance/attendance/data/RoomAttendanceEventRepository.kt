package com.schoollog.attendance.attendance.data

import com.schoollog.attendance.attendance.data.local.SyncStatusValues
import com.schoollog.attendance.attendance.data.local.dao.AttendanceEventDao
import com.schoollog.attendance.attendance.data.local.entity.AttendanceEventEntity
import com.schoollog.attendance.attendance.domain.AttendanceEventDraft
import com.schoollog.attendance.attendance.domain.AttendanceEventRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class RoomAttendanceEventRepository(
    private val attendanceEventDao: AttendanceEventDao,
) : AttendanceEventRepository {
    override val pendingSyncCount: Flow<Int> = attendanceEventDao.observePendingSyncCount()

    override suspend fun saveAttendanceEvent(event: AttendanceEventDraft) {
        val now = System.currentTimeMillis()
        attendanceEventDao.insert(
            AttendanceEventEntity(
                eventId = UUID.randomUUID().toString(),
                schoolId = event.schoolId,
                erpStudentId = event.erpStudentId,
                deviceId = event.deviceId,
                gateId = event.gateId,
                eventType = event.eventType,
                attendanceDate = event.attendanceDate,
                timestampLocal = event.timestampLocal,
                matchScore = event.matchScore,
                livenessScore = event.livenessScore,
                qualityScore = event.qualityScore,
                syncStatus = SyncStatusValues.Pending,
                createdAt = now,
            ),
        )
    }
}
