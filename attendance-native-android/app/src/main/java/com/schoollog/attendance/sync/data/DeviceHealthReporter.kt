package com.schoollog.attendance.sync.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.attendance.data.local.dao.AttendanceEventDao
import com.schoollog.attendance.attendance.data.local.dao.FaceEmbeddingDao
import com.schoollog.attendance.attendance.data.local.dao.FailedRecognitionDao
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.ml.recognition.ModelMetadata
import kotlinx.coroutines.flow.first

class DeviceHealthReporter(
    context: Context,
    private val attendanceEventDao: AttendanceEventDao,
    private val failedRecognitionDao: FailedRecognitionDao,
    private val faceEmbeddingDao: FaceEmbeddingDao,
    private val deviceBindingRepository: DeviceBindingRepository,
    private val syncApi: SyncApi,
) {
    private val appContext = context.applicationContext

    suspend fun sendHealthHeartbeat(lastError: String? = DeviceHealthMonitor.lastError.value): Boolean {
        val binding = deviceBindingRepository.currentBinding() ?: return true
        val modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion
        val battery = appContext.batterySnapshot()
        val pendingAttendanceCount = attendanceEventDao.pendingSyncCount()
        val pendingFailedRecognitionCount = failedRecognitionDao.pendingSyncCount()
        val request = DeviceHeartbeatRequest(
            deviceId = binding.deviceId,
            schoolId = binding.schoolId,
            gateId = binding.gateId,
            timestamp = System.currentTimeMillis().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            modelVersion = modelVersion,
            embeddingCount = faceEmbeddingDao.activeCountForSchoolModel(binding.schoolId, modelVersion),
            pendingAttendanceCount = pendingAttendanceCount,
            pendingFailedRecognitionCount = pendingFailedRecognitionCount,
            lastAttendanceSyncAt = binding.lastAttendanceSyncAtMillis?.toString(),
            lastEmbeddingSyncAt = binding.lastEmbeddingSyncAtMillis?.toString(),
            batteryPercent = battery.percent,
            isCharging = battery.isCharging,
            networkStatus = appContext.networkStatus(),
            networkType = appContext.networkStatus(),
            cameraStatus = DeviceHealthMonitor.cameraStatus.value,
            averageDecisionTime = PerformanceMonitor.metrics.first().averagePipelineDecisionMillis.takeIf { it > 0.0 },
            lastError = lastError,
        )
        return when (syncApi.sendHeartbeat(request)) {
            is SyncApiResult.Success -> {
                deviceBindingRepository.updateLastHeartbeat(System.currentTimeMillis())
                DeviceHealthMonitor.updateLastError(null)
                true
            }
            is SyncApiResult.Error -> {
                DeviceHealthMonitor.updateLastError("Health heartbeat failed")
                false
            }
        }
    }

    private fun Context.batterySnapshot(): BatterySnapshot {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return BatterySnapshot(
            percent = percent,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0,
        )
    }

    private fun Context.networkStatus(): String {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "UNKNOWN"
        val network = manager.activeNetwork ?: return "OFFLINE"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).not() -> "OFFLINE"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "ONLINE"
        }
    }

    private data class BatterySnapshot(
        val percent: Int?,
        val isCharging: Boolean,
    )
}
