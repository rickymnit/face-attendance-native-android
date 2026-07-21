package com.schoollog.attendance.sync.data

interface SyncApi {
    suspend fun registerDevice(request: DeviceRegistrationRequest): SyncApiResult<DeviceRegistrationResponse>
    suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): SyncApiResult<DeviceHeartbeatResponse>
    suspend fun fetchDeviceConfig(deviceId: String, knownConfigVersion: Long? = null): SyncApiResult<DeviceConfigResponse>
    suspend fun fetchEmbeddingDelta(
        schoolId: String,
        sinceVersion: Long,
        modelVersion: String,
    ): SyncApiResult<EmbeddingDeltaResponse>
    suspend fun sendAttendanceEvents(request: AttendanceEventsSyncRequest): SyncApiResult<AttendanceEventsSyncResponse>
    suspend fun sendFailedRecognitionLogs(request: FailedRecognitionLogsSyncRequest): SyncApiResult<FailedRecognitionLogsSyncResponse>
    suspend fun submitEnrollmentRequest(request: EnrollmentRequestSyncRequest): SyncApiResult<EnrollmentRequestResponse>
    suspend fun fetchEnrollmentRequestStatus(requestId: String): SyncApiResult<EnrollmentRequestStatusResponse>
}

sealed interface SyncApiResult<out T> {
    data class Success<T>(val data: T) : SyncApiResult<T>
    data class Error(
        val message: String,
        val type: SyncApiErrorType = SyncApiErrorType.Unknown,
        val retryable: Boolean = true,
        val httpCode: Int? = null,
    ) : SyncApiResult<Nothing>
}

enum class SyncApiErrorType {
    Timeout,
    Unauthorized,
    ServerError,
    BadRequest,
    DuplicateEvent,
    Conflict,
    Network,
    Unknown,
}
