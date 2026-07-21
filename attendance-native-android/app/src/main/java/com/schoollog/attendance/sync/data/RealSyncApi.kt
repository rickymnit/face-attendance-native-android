package com.schoollog.attendance.sync.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RealSyncApi(
    private val service: RetrofitSyncService,
    private val authProvider: SyncAuthProvider = EmptySyncAuthProvider,
) : SyncApi {
    override suspend fun registerDevice(request: DeviceRegistrationRequest): SyncApiResult<DeviceRegistrationResponse> =
        callApi { service.registerDevice(idempotencyKey = UUID.randomUUID().toString(), request = request) }

    override suspend fun sendHeartbeat(request: DeviceHeartbeatRequest): SyncApiResult<DeviceHeartbeatResponse> =
        callApi {
            service.sendHeartbeat(
                authorization = authorizationHeader(),
                deviceId = request.deviceId,
                schoolId = request.schoolId,
                request = request,
            )
        }

    override suspend fun fetchDeviceConfig(
        deviceId: String,
        knownConfigVersion: Long?,
    ): SyncApiResult<DeviceConfigResponse> =
        callApi {
            service.fetchDeviceConfig(
                authorization = authorizationHeader(),
                headerDeviceId = deviceId,
                deviceId = deviceId,
                knownConfigVersion = knownConfigVersion,
            )
        }

    override suspend fun fetchEmbeddingDelta(
        schoolId: String,
        sinceVersion: Long,
        modelVersion: String,
    ): SyncApiResult<EmbeddingDeltaResponse> =
        callApi {
            service.fetchEmbeddingDelta(
                authorization = authorizationHeader(),
                headerSchoolId = schoolId,
                schoolId = schoolId,
                sinceVersion = sinceVersion,
                modelVersion = modelVersion,
            )
        }

    override suspend fun sendAttendanceEvents(
        request: AttendanceEventsSyncRequest,
    ): SyncApiResult<AttendanceEventsSyncResponse> =
        callApi {
            service.syncAttendanceEvents(
                authorization = authorizationHeader(),
                deviceId = request.deviceId,
                schoolId = request.schoolId,
                idempotencyKey = request.events.joinToString(separator = ",") { it.eventId },
                request = request,
            )
        }

    override suspend fun sendFailedRecognitionLogs(
        request: FailedRecognitionLogsSyncRequest,
    ): SyncApiResult<FailedRecognitionLogsSyncResponse> =
        callApi {
            service.syncFailedRecognitionLogs(
                authorization = authorizationHeader(),
                deviceId = request.deviceId,
                schoolId = request.schoolId,
                idempotencyKey = request.logs.joinToString(separator = ",") { it.failedRecognitionId },
                request = request,
            )
        }

    override suspend fun submitEnrollmentRequest(
        request: EnrollmentRequestSyncRequest,
    ): SyncApiResult<EnrollmentRequestResponse> =
        callApi {
            service.submitEnrollmentRequest(
                authorization = authorizationHeader(),
                deviceId = request.deviceId,
                schoolId = request.schoolId,
                idempotencyKey = request.requestId,
                request = request,
            )
        }

    override suspend fun fetchEnrollmentRequestStatus(
        requestId: String,
    ): SyncApiResult<EnrollmentRequestStatusResponse> =
        callApi { service.fetchEnrollmentRequestStatus(authorization = authorizationHeader(), requestId = requestId) }

    private fun authorizationHeader(): String? =
        authProvider.deviceAccessToken()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    private suspend fun <T> callApi(block: suspend () -> Response<T>): SyncApiResult<T> =
        try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    SyncApiResult.Success(body)
                } else {
                    SyncApiResult.Error(
                        message = "Empty API response",
                        type = SyncApiErrorType.ServerError,
                        retryable = true,
                        httpCode = response.code(),
                    )
                }
            } else {
                response.toSyncError()
            }
        } catch (exception: SocketTimeoutException) {
            SyncApiResult.Error(
                message = exception.message ?: "API timeout",
                type = SyncApiErrorType.Timeout,
                retryable = true,
            )
        } catch (exception: IOException) {
            SyncApiResult.Error(
                message = exception.message ?: "Network error",
                type = SyncApiErrorType.Network,
                retryable = true,
            )
        } catch (exception: Exception) {
            SyncApiResult.Error(
                message = exception.message ?: "Unexpected sync error",
                type = SyncApiErrorType.Unknown,
                retryable = true,
            )
        }

    private fun <T> Response<T>.toSyncError(): SyncApiResult.Error {
        val code = code()
        val errorText = runCatching { errorBody()?.string().orEmpty() }.getOrDefault("")
        val type = when (code) {
            400 -> SyncApiErrorType.BadRequest
            401, 403 -> SyncApiErrorType.Unauthorized
            409 -> if (errorText.contains("DUPLICATE", ignoreCase = true)) {
                SyncApiErrorType.DuplicateEvent
            } else {
                SyncApiErrorType.Conflict
            }
            in 500..599 -> SyncApiErrorType.ServerError
            else -> SyncApiErrorType.Unknown
        }
        return SyncApiResult.Error(
            message = errorText.ifBlank { "API error HTTP $code" },
            type = type,
            retryable = type == SyncApiErrorType.ServerError || type == SyncApiErrorType.Network || type == SyncApiErrorType.Timeout,
            httpCode = code,
        )
    }

    companion object {
        fun create(
            baseUrl: String,
            authProvider: SyncAuthProvider = EmptySyncAuthProvider,
        ): RealSyncApi {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(ConnectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(ReadTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(WriteTimeoutSeconds, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return RealSyncApi(
                service = retrofit.create(RetrofitSyncService::class.java),
                authProvider = authProvider,
            )
        }

        private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

        private const val ConnectTimeoutSeconds = 15L
        private const val ReadTimeoutSeconds = 30L
        private const val WriteTimeoutSeconds = 30L
    }
}
