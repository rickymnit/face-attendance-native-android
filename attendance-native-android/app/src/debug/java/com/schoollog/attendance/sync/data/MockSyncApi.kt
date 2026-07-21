package com.schoollog.attendance.sync.data

import kotlinx.coroutines.delay

class MockSyncApi : SyncApi {
    override suspend fun registerDevice(request: DeviceRegistrationRequest): SyncApiResult<DeviceRegistrationResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            DeviceRegistrationResponse(
                deviceId = "debug-device",
                schoolId = "debug-${request.schoolCode}",
                schoolCode = request.schoolCode,
                schoolName = "Debug School",
                gateId = request.gateId,
                deviceAccessToken = "debug-token",
                refreshToken = "debug-refresh-token",
                configVersion = 1,
                embeddingSyncVersion = 0L,
                config = null,
                serverTime = System.currentTimeMillis().toString(),
            ),
        )
    }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): SyncApiResult<DeviceHeartbeatResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            DeviceHeartbeatResponse(
                accepted = true,
                serverTime = System.currentTimeMillis().toString(),
                configVersion = 1,
            ),
        )
    }

    override suspend fun fetchDeviceConfig(
        deviceId: String,
        knownConfigVersion: Long?,
    ): SyncApiResult<DeviceConfigResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Error("Mock config endpoint is not seeded", retryable = false)
    }

    override suspend fun fetchEmbeddingDelta(
        schoolId: String,
        sinceVersion: Long,
        modelVersion: String,
    ): SyncApiResult<EmbeddingDeltaResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            EmbeddingDeltaResponse(
                schoolId = schoolId,
                modelVersion = modelVersion,
                fromVersion = sinceVersion,
                toVersion = sinceVersion,
                hasMore = false,
            ),
        )
    }

    override suspend fun sendAttendanceEvents(
        request: AttendanceEventsSyncRequest,
    ): SyncApiResult<AttendanceEventsSyncResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            AttendanceEventsSyncResponse(
                accepted = request.events.map { event ->
                    AttendanceEventSyncAccepted(
                        eventId = event.eventId,
                        status = "ACCEPTED",
                        erpReferenceId = "mock-${event.eventId}",
                    )
                },
            ),
        )
    }

    override suspend fun sendFailedRecognitionLogs(
        request: FailedRecognitionLogsSyncRequest,
    ): SyncApiResult<FailedRecognitionLogsSyncResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            FailedRecognitionLogsSyncResponse(
                accepted = request.logs.map { log ->
                    FailedRecognitionLogSyncAccepted(
                        failedRecognitionId = log.failedRecognitionId,
                        status = "ACCEPTED",
                    )
                },
            ),
        )
    }

    override suspend fun submitEnrollmentRequest(
        request: EnrollmentRequestSyncRequest,
    ): SyncApiResult<EnrollmentRequestResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Success(
            EnrollmentRequestResponse(
                requestId = request.requestId,
                status = "PENDING_APPROVAL",
                receivedAt = System.currentTimeMillis().toString(),
            ),
        )
    }

    override suspend fun fetchEnrollmentRequestStatus(
        requestId: String,
    ): SyncApiResult<EnrollmentRequestStatusResponse> {
        delay(MockNetworkDelayMillis)
        return SyncApiResult.Error("Mock enrollment status endpoint is not seeded", retryable = false)
    }

    private companion object {
        const val MockNetworkDelayMillis = 350L
    }
}
