package com.schoollog.attendance.sync.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RetrofitSyncService {
    @POST("api/devices/register")
    suspend fun registerDevice(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: DeviceRegistrationRequest,
    ): Response<DeviceRegistrationResponse>

    @POST("api/devices/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-Device-Id") deviceId: String,
        @Header("X-Schoollog-School-Id") schoolId: String,
        @Body request: DeviceHeartbeatRequest,
    ): Response<DeviceHeartbeatResponse>

    @GET("api/devices/{deviceId}/config")
    suspend fun fetchDeviceConfig(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-Device-Id") headerDeviceId: String,
        @Path("deviceId") deviceId: String,
        @Query("knownConfigVersion") knownConfigVersion: Long?,
    ): Response<DeviceConfigResponse>

    @GET("api/schools/{schoolId}/embeddings/delta")
    suspend fun fetchEmbeddingDelta(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-School-Id") headerSchoolId: String,
        @Path("schoolId") schoolId: String,
        @Query("sinceVersion") sinceVersion: Long,
        @Query("modelVersion") modelVersion: String,
    ): Response<EmbeddingDeltaResponse>

    @POST("api/attendance/events/sync")
    suspend fun syncAttendanceEvents(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-Device-Id") deviceId: String,
        @Header("X-Schoollog-School-Id") schoolId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: AttendanceEventsSyncRequest,
    ): Response<AttendanceEventsSyncResponse>

    @POST("api/attendance/failed/sync")
    suspend fun syncFailedRecognitionLogs(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-Device-Id") deviceId: String,
        @Header("X-Schoollog-School-Id") schoolId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: FailedRecognitionLogsSyncRequest,
    ): Response<FailedRecognitionLogsSyncResponse>

    @POST("api/enrollment/requests")
    suspend fun submitEnrollmentRequest(
        @Header("Authorization") authorization: String?,
        @Header("X-Schoollog-Device-Id") deviceId: String,
        @Header("X-Schoollog-School-Id") schoolId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: EnrollmentRequestSyncRequest,
    ): Response<EnrollmentRequestResponse>

    @GET("api/enrollment/requests/{id}")
    suspend fun fetchEnrollmentRequestStatus(
        @Header("Authorization") authorization: String?,
        @Path("id") requestId: String,
    ): Response<EnrollmentRequestStatusResponse>
}
