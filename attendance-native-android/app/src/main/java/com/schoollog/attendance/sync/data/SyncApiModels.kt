package com.schoollog.attendance.sync.data

data class DeviceRegistrationRequest(
    val schoolCode: String,
    val gateId: String,
    val deviceName: String,
    val setupToken: String,
    val deviceInfo: DeviceInfoRequest,
)

data class DeviceInfoRequest(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
)

data class DeviceRegistrationResponse(
    val deviceId: String,
    val schoolId: String,
    val schoolCode: String? = null,
    val schoolName: String? = null,
    val gateId: String,
    val deviceAccessToken: String,
    val refreshToken: String?,
    val configVersion: Long,
    val embeddingSyncVersion: Long = 0L,
    val config: DeviceConfigResponse? = null,
    val serverTime: String,
)

data class DeviceHeartbeatRequest(
    val deviceId: String,
    val schoolId: String,
    val gateId: String,
    val timestamp: String,
    val appVersion: String,
    val modelVersion: String,
    val embeddingCount: Int = 0,
    val pendingAttendanceCount: Int,
    val pendingFailedRecognitionCount: Int,
    val lastAttendanceSyncAt: String? = null,
    val lastEmbeddingSyncAt: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val networkStatus: String? = null,
    val networkType: String? = null,
    val cameraStatus: String? = null,
    val averageDecisionTime: Double? = null,
    val lastError: String? = null,
)

data class DeviceHeartbeatResponse(
    val accepted: Boolean,
    val serverTime: String,
    val configVersion: Long? = null,
    val requiresConfigRefresh: Boolean = false,
    val requiresEmbeddingDeltaRefresh: Boolean = false,
)

data class DeviceConfigResponse(
    val deviceId: String,
    val schoolId: String,
    val gateId: String,
    val configVersion: Long,
    val attendanceRules: RemoteAttendanceRules,
    val syncRules: RemoteSyncRules,
    val model: RemoteModelConfig,
)

data class RemoteAttendanceRules(
    val schoolStartTime: String,
    val lateAfterTime: String,
    val halfDayAfterTime: String,
    val duplicateScanCooldownMinutes: Int,
    val requireOutTime: Boolean,
    val recognitionMode: String,
)

data class RemoteSyncRules(
    val attendanceBatchSize: Int,
    val failedRecognitionBatchSize: Int,
    val heartbeatIntervalMinutes: Int,
)

data class RemoteModelConfig(
    val modelVersion: String,
    val embeddingSize: Int,
    val distanceMetric: String,
)

data class EmbeddingDeltaResponse(
    val schoolId: String,
    val modelVersion: String,
    val fromVersion: Long,
    val toVersion: Long,
    val hasMore: Boolean,
    val embeddings: List<RemoteEmbedding> = emptyList(),
    val deletedEmbeddings: List<RemoteDeletedEmbedding> = emptyList(),
)

data class RemoteEmbedding(
    val erpStudentId: String,
    val changeType: String = "UPSERT",
    val student: RemoteStudent,
    val embeddingBase64: String,
    val embeddingSize: Int,
    val qualityScore: Float,
    val status: String,
    val updatedAt: String,
)

data class RemoteStudent(
    val name: String,
    val className: String,
    val section: String,
    val rollNumber: String,
    val status: String,
)

data class RemoteDeletedEmbedding(
    val erpStudentId: String,
    val modelVersion: String,
    val deletedAt: String,
)

data class AttendanceEventsSyncRequest(
    val deviceId: String,
    val schoolId: String,
    val gateId: String,
    val events: List<AttendanceEventSyncRequest>,
)

data class AttendanceEventSyncRequest(
    val eventId: String,
    val schoolId: String,
    val studentId: String,
    val deviceId: String,
    val gateId: String,
    val eventType: String,
    val attendanceDate: String,
    val timestamp: Long,
    val matchScore: Float,
    val livenessScore: Float,
    val qualityScore: Float,
    val modelVersion: String? = null,
    val recognitionMode: String? = null,
)

data class AttendanceEventsSyncResponse(
    val accepted: List<AttendanceEventSyncAccepted> = emptyList(),
    val rejected: List<AttendanceEventSyncRejected> = emptyList(),
    val serverTime: String? = null,
)

data class AttendanceEventSyncAccepted(
    val eventId: String,
    val status: String,
    val erpReferenceId: String? = null,
)

data class AttendanceEventSyncRejected(
    val eventId: String,
    val status: String,
    val code: String,
    val message: String,
    val retryable: Boolean,
)

data class FailedRecognitionLogsSyncRequest(
    val deviceId: String,
    val schoolId: String,
    val gateId: String,
    val logs: List<FailedRecognitionLogSyncRequest>,
)

data class FailedRecognitionLogSyncRequest(
    val failedRecognitionId: String,
    val reason: String,
    val timestamp: Long,
    val qualityScore: Float,
    val livenessScore: Float,
    val operatorAction: String?,
)

data class FailedRecognitionLogsSyncResponse(
    val accepted: List<FailedRecognitionLogSyncAccepted> = emptyList(),
    val rejected: List<FailedRecognitionLogSyncRejected> = emptyList(),
)

data class FailedRecognitionLogSyncAccepted(
    val failedRecognitionId: String,
    val status: String,
)

data class FailedRecognitionLogSyncRejected(
    val failedRecognitionId: String,
    val status: String,
    val code: String,
    val message: String,
    val retryable: Boolean,
)

data class EnrollmentRequestSyncRequest(
    val requestId: String,
    val deviceId: String,
    val schoolId: String,
    val gateId: String,
    val erpStudentId: String,
    val student: RemoteStudent,
    val embedding: EnrollmentEmbeddingRequest,
    val submittedAt: String,
)

data class EnrollmentEmbeddingRequest(
    val modelVersion: String,
    val embeddingSize: Int,
    val embeddingBase64: String,
    val qualityScore: Float,
    val source: String,
)

data class EnrollmentRequestResponse(
    val requestId: String,
    val status: String,
    val receivedAt: String,
)

data class EnrollmentRequestStatusResponse(
    val requestId: String,
    val schoolId: String,
    val erpStudentId: String,
    val status: String,
    val reviewedAt: String?,
    val reviewedBy: String?,
    val message: String?,
    val embeddingDeltaVersion: Long?,
)
