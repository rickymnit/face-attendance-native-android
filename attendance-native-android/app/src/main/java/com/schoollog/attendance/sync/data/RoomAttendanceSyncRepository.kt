package com.schoollog.attendance.sync.data

import com.schoollog.attendance.attendance.data.local.SyncStatusValues
import com.schoollog.attendance.attendance.data.local.dao.AttendanceEventDao
import com.schoollog.attendance.attendance.data.local.dao.FailedRecognitionDao
import com.schoollog.attendance.attendance.data.local.entity.AttendanceEventEntity
import com.schoollog.attendance.attendance.data.local.entity.FailedRecognitionEntity
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.sync.domain.AttendanceSyncRepository
import com.schoollog.attendance.sync.domain.SyncRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class RoomAttendanceSyncRepository(
    private val attendanceEventDao: AttendanceEventDao,
    private val failedRecognitionDao: FailedRecognitionDao,
    private val settingsRepository: SettingsRepository,
    private val deviceBindingRepository: DeviceBindingRepository,
    private val syncApi: SyncApi,
) : AttendanceSyncRepository {
    private val _lastSyncStatus = MutableStateFlow<SyncRunStatus>(SyncRunStatus.Idle)
    override val lastSyncStatus: StateFlow<SyncRunStatus> = _lastSyncStatus.asStateFlow()

    override suspend fun syncPendingAttendanceEvents(): SyncRunStatus {
        _lastSyncStatus.value = SyncRunStatus.Running
        val rules = settingsRepository.attendanceRules.first()
        val attendanceEvents = attendanceEventDao.pendingEvents()
        val failedRecognitions = failedRecognitionDao.pendingFailures()
        sendHeartbeat(rules, attendanceEvents.size, failedRecognitions.size)

        if (attendanceEvents.isEmpty() && failedRecognitions.isEmpty()) {
            return publishSuccess(syncedCount = 0)
        }

        var syncedCount = 0
        var duplicateCount = 0
        val errors = mutableListOf<String>()

        if (attendanceEvents.isNotEmpty()) {
            when (val result = syncAttendanceBatch(attendanceEvents, rules)) {
                is BatchSyncResult.Success -> {
                    syncedCount += result.syncedCount
                    duplicateCount += result.duplicateCount
                }
                is BatchSyncResult.Failed -> errors += result.message
            }
        }

        if (failedRecognitions.isNotEmpty()) {
            when (val result = syncFailedRecognitionBatch(failedRecognitions, rules)) {
                is BatchSyncResult.Success -> {
                    syncedCount += result.syncedCount
                    duplicateCount += result.duplicateCount
                }
                is BatchSyncResult.Failed -> errors += result.message
            }
        }

        return if (errors.isEmpty()) {
            publishSuccess(syncedCount, duplicateCount)
        } else {
            publishFailure(errors.joinToString(separator = "; "))
        }
    }


    private suspend fun sendHeartbeat(
        rules: AttendanceRules,
        pendingAttendanceCount: Int,
        pendingFailedRecognitionCount: Int,
    ) {
        val binding = deviceBindingRepository.currentBinding() ?: return
        val request = DeviceHeartbeatRequest(
            deviceId = binding.deviceId,
            schoolId = binding.schoolId,
            gateId = binding.gateId,
            timestamp = System.currentTimeMillis().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
            pendingAttendanceCount = pendingAttendanceCount,
            pendingFailedRecognitionCount = pendingFailedRecognitionCount,
        )
        when (syncApi.sendHeartbeat(request)) {
            is SyncApiResult.Success -> deviceBindingRepository.updateLastHeartbeat(System.currentTimeMillis())
            is SyncApiResult.Error -> Unit
        }
    }

    private suspend fun syncAttendanceBatch(
        pendingEvents: List<AttendanceEventEntity>,
        rules: AttendanceRules,
    ): BatchSyncResult {
        val request = AttendanceEventsSyncRequest(
            deviceId = rules.deviceId,
            schoolId = rules.schoolId,
            gateId = rules.gateId,
            events = pendingEvents.map { it.toSyncRequest(rules) },
        )
        return when (val result = syncApi.sendAttendanceEvents(request)) {
            is SyncApiResult.Success -> {
                val acceptedIds = result.data.accepted.map { it.eventId }
                val duplicateCount = result.data.accepted.count { it.status.contains("duplicate", ignoreCase = true) }
                val failedIds = result.data.rejected
                    .filterNot { it.retryable }
                    .map { it.eventId }
                if (acceptedIds.isNotEmpty()) {
                    attendanceEventDao.updateSyncStatus(acceptedIds, SyncStatusValues.Synced)
                }
                if (failedIds.isNotEmpty()) {
                    attendanceEventDao.updateSyncStatus(failedIds, SyncStatusValues.Failed)
                }
                val retryableRejected = result.data.rejected.filter { it.retryable }
                if (retryableRejected.isNotEmpty()) {
                    BatchSyncResult.Failed("Attendance sync has ${retryableRejected.size} retryable rejected event(s)")
                } else {
                    BatchSyncResult.Success(syncedCount = acceptedIds.size, duplicateCount = duplicateCount)
                }
            }
            is SyncApiResult.Error -> {
                if (result.shouldMarkLocalRowsFailed()) {
                    attendanceEventDao.updateSyncStatus(
                        eventIds = pendingEvents.map { it.eventId },
                        syncStatus = SyncStatusValues.Failed,
                    )
                }
                BatchSyncResult.Failed("Attendance sync failed: ${result.type} ${result.message}")
            }
        }
    }

    private suspend fun syncFailedRecognitionBatch(
        pendingFailures: List<FailedRecognitionEntity>,
        rules: AttendanceRules,
    ): BatchSyncResult {
        val request = FailedRecognitionLogsSyncRequest(
            deviceId = rules.deviceId,
            schoolId = rules.schoolId,
            gateId = rules.gateId,
            logs = pendingFailures.map { it.toSyncRequest() },
        )
        return when (val result = syncApi.sendFailedRecognitionLogs(request)) {
            is SyncApiResult.Success -> {
                val acceptedIds = result.data.accepted.map { it.failedRecognitionId }
                val failedIds = result.data.rejected
                    .filterNot { it.retryable }
                    .map { it.failedRecognitionId }
                if (acceptedIds.isNotEmpty()) {
                    failedRecognitionDao.updateSyncStatus(acceptedIds, SyncStatusValues.Synced)
                }
                if (failedIds.isNotEmpty()) {
                    failedRecognitionDao.updateSyncStatus(failedIds, SyncStatusValues.Failed)
                }
                val retryableRejected = result.data.rejected.filter { it.retryable }
                if (retryableRejected.isNotEmpty()) {
                    BatchSyncResult.Failed("Failed-recognition sync has ${retryableRejected.size} retryable rejected log(s)")
                } else {
                    BatchSyncResult.Success(syncedCount = acceptedIds.size, duplicateCount = 0)
                }
            }
            is SyncApiResult.Error -> {
                if (result.shouldMarkLocalRowsFailed()) {
                    failedRecognitionDao.updateSyncStatus(
                        ids = pendingFailures.map { it.id },
                        syncStatus = SyncStatusValues.Failed,
                    )
                }
                BatchSyncResult.Failed("Failed-recognition sync failed: ${result.type} ${result.message}")
            }
        }
    }

    private fun AttendanceEventEntity.toSyncRequest(rules: AttendanceRules): AttendanceEventSyncRequest =
        AttendanceEventSyncRequest(
            eventId = eventId,
            schoolId = schoolId,
            studentId = erpStudentId,
            deviceId = deviceId,
            gateId = gateId,
            eventType = eventType,
            attendanceDate = attendanceDate,
            timestamp = timestampLocal,
            matchScore = matchScore,
            livenessScore = livenessScore,
            qualityScore = qualityScore,
            modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
            recognitionMode = rules.recognitionMode.name,
        )

    private fun FailedRecognitionEntity.toSyncRequest(): FailedRecognitionLogSyncRequest =
        FailedRecognitionLogSyncRequest(
            failedRecognitionId = id,
            reason = reason,
            timestamp = timestamp,
            qualityScore = qualityScore,
            livenessScore = livenessScore,
            operatorAction = operatorAction,
        )

    private fun SyncApiResult.Error.shouldMarkLocalRowsFailed(): Boolean =
        type == SyncApiErrorType.BadRequest ||
            type == SyncApiErrorType.Conflict ||
            type == SyncApiErrorType.DuplicateEvent

    private fun publishSuccess(syncedCount: Int, duplicateCount: Int = 0): SyncRunStatus.Success {
        val status = SyncRunStatus.Success(
            syncedCount = syncedCount,
            completedAtMillis = System.currentTimeMillis(),
            duplicateCount = duplicateCount,
        )
        _lastSyncStatus.value = status
        return status
    }

    private fun publishFailure(message: String): SyncRunStatus.Failed {
        val status = SyncRunStatus.Failed(
            message = message,
            completedAtMillis = System.currentTimeMillis(),
        )
        _lastSyncStatus.value = status
        return status
    }

    private sealed interface BatchSyncResult {
        data class Success(val syncedCount: Int, val duplicateCount: Int = 0) : BatchSyncResult
        data class Failed(val message: String) : BatchSyncResult
    }
}
