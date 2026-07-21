package com.schoollog.attendance.app.pilot

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.attendance.domain.AttendanceEventRepository
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.DeviceBinding
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.debugqa.data.TfliteModelSmokeTester
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.sync.data.DeviceHeartbeatRequest
import com.schoollog.attendance.sync.data.SyncApi
import com.schoollog.attendance.sync.data.SyncApiResult
import com.schoollog.attendance.sync.domain.SyncRunStatus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PilotReadinessScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val appContainer = (context as SchoollogAttendanceApp).appContainer
    val binding by appContainer.deviceBindingRepository.deviceBinding.collectAsStateWithLifecycle(initialValue = null)
    val rules by appContainer.settingsRepository.attendanceRules.collectAsStateWithLifecycle(initialValue = AttendanceRules())
    val pendingSyncCount by appContainer.attendanceEventRepository.pendingSyncCount.collectAsStateWithLifecycle(initialValue = 0)
    val attendanceSyncStatus by appContainer.attendanceSyncRepository.lastSyncStatus.collectAsStateWithLifecycle()
    val embeddingSyncStatus by appContainer.embeddingSyncRepository.lastEmbeddingSyncStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(defaultReadinessItems()) }

    LaunchedEffect(binding, rules, pendingSyncCount, attendanceSyncStatus, embeddingSyncStatus) {
        if (!isRunning) {
            items = localReadinessSnapshot(
                context = context,
                binding = binding,
                rules = rules,
                pendingSyncCount = pendingSyncCount,
                attendanceSyncStatus = attendanceSyncStatus,
                embeddingSyncStatus = embeddingSyncStatus,
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pilot Readiness",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Admin/debug checks before gate deployment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            items = runReadinessChecks(
                                context = context,
                                bindingRepository = appContainer.deviceBindingRepository,
                                attendanceEventRepository = appContainer.attendanceEventRepository,
                                faceEmbeddingRepository = appContainer.faceEmbeddingRepository,
                                syncApi = appContainer.syncApi,
                                rules = rules,
                                attendanceSyncStatus = attendanceSyncStatus,
                                embeddingSyncStatus = embeddingSyncStatus,
                            )
                            isRunning = false
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRunning) "Running Readiness Check..." else "Run Readiness Check")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isRunning = true
                            appContainer.embeddingSyncRepository.syncEmbeddingDelta()
                            items = runReadinessChecks(
                                context = context,
                                bindingRepository = appContainer.deviceBindingRepository,
                                attendanceEventRepository = appContainer.attendanceEventRepository,
                                faceEmbeddingRepository = appContainer.faceEmbeddingRepository,
                                syncApi = appContainer.syncApi,
                                rules = rules,
                                attendanceSyncStatus = attendanceSyncStatus,
                                embeddingSyncStatus = appContainer.embeddingSyncRepository.lastEmbeddingSyncStatus.value,
                            )
                            isRunning = false
                        }
                    },
                    enabled = !isRunning && binding != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sync Embeddings Now")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            items(items) { item ->
                ReadinessRow(item)
            }
        }
    }
}

@Composable
private fun ReadinessRow(item: ReadinessItem) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = item.status.name,
                color = item.status.color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.26f),
            )
            Column(modifier = Modifier.weight(0.74f)) {
                Text(text = item.title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun runReadinessChecks(
    context: Context,
    bindingRepository: DeviceBindingRepository,
    attendanceEventRepository: AttendanceEventRepository,
    faceEmbeddingRepository: FaceEmbeddingRepository,
    syncApi: SyncApi,
    rules: AttendanceRules,
    attendanceSyncStatus: SyncRunStatus,
    embeddingSyncStatus: SyncRunStatus,
): List<ReadinessItem> {
    val binding = bindingRepository.currentBinding()
    val pendingSyncCount = attendanceEventRepository.pendingSyncCount.first()
    val smokeTest = TfliteModelSmokeTester(context).run()
    val activeEmbeddings = binding?.let {
        faceEmbeddingRepository.getActiveEmbeddingsForSchool(
            schoolId = it.schoolId,
            modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
        ).size
    } ?: 0
    val apiResult = binding?.let {
        syncApi.fetchDeviceConfig(deviceId = it.deviceId, knownConfigVersion = it.configVersion)
    }
    val heartbeatResult = binding?.let {
        syncApi.sendHeartbeat(
            DeviceHeartbeatRequest(
                deviceId = it.deviceId,
                schoolId = it.schoolId,
                gateId = it.gateId,
                timestamp = System.currentTimeMillis().toString(),
                appVersion = BuildConfig.VERSION_NAME,
                modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
                pendingAttendanceCount = pendingSyncCount,
                pendingFailedRecognitionCount = 0,
                batteryPercent = context.batteryPercent(),
                isCharging = context.isCharging(),
                networkType = if (context.isOnline()) "ONLINE" else "OFFLINE",
            ),
        )
    }
    if (heartbeatResult is SyncApiResult.Success) {
        bindingRepository.updateLastHeartbeat(System.currentTimeMillis())
    }
    return buildReadinessItems(
        context = context,
        binding = bindingRepository.currentBinding() ?: binding,
        rules = rules,
        pendingSyncCount = pendingSyncCount,
        smokeModelFound = smokeTest.modelFound,
        smokePassed = smokeTest.success,
        activeEmbeddings = activeEmbeddings,
        apiResult = apiResult,
        attendanceSyncStatus = attendanceSyncStatus,
        embeddingSyncStatus = embeddingSyncStatus,
    )
}

private fun localReadinessSnapshot(
    context: Context,
    binding: DeviceBinding?,
    rules: AttendanceRules,
    pendingSyncCount: Int,
    attendanceSyncStatus: SyncRunStatus,
    embeddingSyncStatus: SyncRunStatus,
): List<ReadinessItem> = buildReadinessItems(
    context = context,
    binding = binding,
    rules = rules,
    pendingSyncCount = pendingSyncCount,
    smokeModelFound = null,
    smokePassed = null,
    activeEmbeddings = null,
    apiResult = null,
    attendanceSyncStatus = attendanceSyncStatus,
    embeddingSyncStatus = embeddingSyncStatus,
)

private fun defaultReadinessItems(): List<ReadinessItem> = ReadinessTitle.values().map {
    ReadinessItem(it.label, ReadinessStatus.WARNING, "Run readiness check")
}

private fun buildReadinessItems(
    context: Context,
    binding: DeviceBinding?,
    rules: AttendanceRules,
    pendingSyncCount: Int,
    smokeModelFound: Boolean?,
    smokePassed: Boolean?,
    activeEmbeddings: Int?,
    apiResult: SyncApiResult<*>?,
    attendanceSyncStatus: SyncRunStatus,
    embeddingSyncStatus: SyncRunStatus,
): List<ReadinessItem> {
    val batteryPercent = context.batteryPercent()
    val charging = context.isCharging()
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val lockTask = context.isLockTaskActive()
    return listOf(
        item(ReadinessTitle.DeviceRegistered, binding != null, "Device ${binding?.deviceId ?: "not registered"}"),
        item(ReadinessTitle.SchoolBound, !binding?.schoolId.isNullOrBlank(), "School ${binding?.schoolName ?: binding?.schoolId ?: "not bound"}"),
        item(ReadinessTitle.GateConfigured, !binding?.gateId.isNullOrBlank(), "Gate ${binding?.gateId ?: "not configured"}"),
        nullableItem(ReadinessTitle.FaceModelInstalled, smokeModelFound, "Model asset models/face_embedding.tflite"),
        nullableItem(ReadinessTitle.ModelSmokeTestPassed, smokePassed, "Version ${ModelMetadata.DefaultFaceEmbedding.modelVersion}"),
        when (activeEmbeddings) {
            null -> ReadinessItem(ReadinessTitle.EmbeddingsLoaded.label, ReadinessStatus.WARNING, "Run readiness check")
            0 -> ReadinessItem(ReadinessTitle.EmbeddingsLoaded.label, ReadinessStatus.FAIL, "No enrolled students found")
            else -> ReadinessItem(ReadinessTitle.EmbeddingsLoaded.label, ReadinessStatus.PASS, "$activeEmbeddings active embedding(s)")
        },
        ReadinessItem(
            title = ReadinessTitle.BackendEmbeddingSyncVersion.label,
            status = if ((binding?.embeddingSyncVersion ?: 0L) > 0L) ReadinessStatus.PASS else ReadinessStatus.WARNING,
            detail = "Backend sync version ${binding?.embeddingSyncVersion ?: 0L}",
        ),
        ReadinessItem(
            title = ReadinessTitle.EmbeddingModelVersion.label,
            status = ReadinessStatus.PASS,
            detail = ModelMetadata.DefaultFaceEmbedding.modelVersion,
        ),
        ReadinessItem(
            title = ReadinessTitle.ThresholdModeSet.label,
            status = if (rules.recognitionMode == RecognitionMode.Strict) ReadinessStatus.PASS else ReadinessStatus.WARNING,
            detail = "Mode ${rules.recognitionMode.name}; production default is Strict",
        ),
        apiItem(apiResult),
        ReadinessItem(
            title = ReadinessTitle.PendingSyncCount.label,
            status = if (pendingSyncCount == 0) ReadinessStatus.PASS else ReadinessStatus.WARNING,
            detail = "$pendingSyncCount pending attendance event(s)",
        ),
        ReadinessItem(
            title = ReadinessTitle.ChargerConnected.label,
            status = when {
                charging -> ReadinessStatus.PASS
                batteryPercent < 30 -> ReadinessStatus.FAIL
                else -> ReadinessStatus.WARNING
            },
            detail = if (charging) "Charging, battery $batteryPercent%" else "Not charging, battery $batteryPercent%",
        ),
        ReadinessItem(
            title = ReadinessTitle.CameraPermissionGranted.label,
            status = if (cameraGranted) ReadinessStatus.PASS else ReadinessStatus.FAIL,
            detail = if (cameraGranted) "Camera permission granted" else "Open Gate Mode once and grant camera permission",
        ),
        ReadinessItem(
            title = ReadinessTitle.KioskModeRecommended.label,
            status = if (lockTask) ReadinessStatus.PASS else ReadinessStatus.WARNING,
            detail = if (lockTask) "Lock task mode active" else "Enable pinned/lock task mode before pilot",
        ),
        ReadinessItem(
            title = ReadinessTitle.LastSuccessfulHeartbeat.label,
            status = binding.lastHeartbeatStatus(),
            detail = binding?.lastHeartbeatAtMillis?.let { "Last heartbeat ${it.formatTime()}" } ?: "No heartbeat recorded",
        ),
        ReadinessItem(
            title = ReadinessTitle.LastAttendanceSync.label,
            status = attendanceSyncStatus.successStatus(),
            detail = attendanceSyncStatus.readinessText(),
        ),
        ReadinessItem(
            title = ReadinessTitle.LastEmbeddingSync.label,
            status = embeddingSyncStatus.successStatus(),
            detail = embeddingSyncStatus.readinessText(),
        ),
    )
}

private fun item(title: ReadinessTitle, passed: Boolean, detail: String): ReadinessItem =
    ReadinessItem(title.label, if (passed) ReadinessStatus.PASS else ReadinessStatus.FAIL, detail)

private fun nullableItem(title: ReadinessTitle, passed: Boolean?, detail: String): ReadinessItem =
    ReadinessItem(
        title = title.label,
        status = when (passed) {
            true -> ReadinessStatus.PASS
            false -> ReadinessStatus.FAIL
            null -> ReadinessStatus.WARNING
        },
        detail = if (passed == null) "Run readiness check" else detail,
    )

private fun apiItem(result: SyncApiResult<*>?): ReadinessItem =
    when (result) {
        is SyncApiResult.Success -> ReadinessItem(ReadinessTitle.SyncApiReachable.label, ReadinessStatus.PASS, "Configured API is reachable")
        is SyncApiResult.Error -> ReadinessItem(ReadinessTitle.SyncApiReachable.label, if (result.retryable) ReadinessStatus.WARNING else ReadinessStatus.FAIL, "${result.type}: ${result.message}")
        null -> ReadinessItem(ReadinessTitle.SyncApiReachable.label, ReadinessStatus.WARNING, "Run readiness check")
    }

private fun DeviceBinding?.lastHeartbeatStatus(): ReadinessStatus {
    val last = this?.lastHeartbeatAtMillis ?: return ReadinessStatus.WARNING
    val ageMillis = System.currentTimeMillis() - last
    return if (ageMillis <= 30 * 60 * 1000L) ReadinessStatus.PASS else ReadinessStatus.WARNING
}

private fun SyncRunStatus.successStatus(): ReadinessStatus =
    when (this) {
        is SyncRunStatus.Success -> ReadinessStatus.PASS
        is SyncRunStatus.Failed -> ReadinessStatus.WARNING
        SyncRunStatus.Idle -> ReadinessStatus.WARNING
        SyncRunStatus.Running -> ReadinessStatus.WARNING
    }

private fun SyncRunStatus.readinessText(): String =
    when (this) {
        SyncRunStatus.Idle -> "No sync run recorded in this app session"
        SyncRunStatus.Running -> "Sync running"
        is SyncRunStatus.Success -> if (duplicateCount > 0) {
            "Synced $syncedCount item(s), $duplicateCount duplicate(s) accepted at ${completedAtMillis.formatTime()}"
        } else {
            "Synced $syncedCount item(s) at ${completedAtMillis.formatTime()}"
        }
        is SyncRunStatus.Failed -> "Last sync failed at ${completedAtMillis.formatTime()}: $message"
    }

private fun Context.batteryPercent(): Int {
    val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) (level * 100) / scale else 100
}

private fun Context.isCharging(): Boolean {
    val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
}

private fun Context.isOnline(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

private fun Context.isLockTaskActive(): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    } else {
        @Suppress("DEPRECATION")
        manager.isInLockTaskMode
    }
}

private fun Long.formatTime(): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))

private data class ReadinessItem(
    val title: String,
    val status: ReadinessStatus,
    val detail: String,
)

private enum class ReadinessStatus(val color: Color) {
    PASS(Color(0xFF1B8A3A)),
    WARNING(Color(0xFF9A6A00)),
    FAIL(Color(0xFFB3261E)),
}

private enum class ReadinessTitle(val label: String) {
    DeviceRegistered("Device registered"),
    SchoolBound("School bound"),
    GateConfigured("Gate configured"),
    FaceModelInstalled("Face model installed"),
    ModelSmokeTestPassed("Model smoke test passed"),
    EmbeddingsLoaded("Local embedding count"),
    BackendEmbeddingSyncVersion("Backend embedding sync version"),
    EmbeddingModelVersion("Embedding model version"),
    ThresholdModeSet("Recognition threshold mode set"),
    SyncApiReachable("Sync API reachable"),
    PendingSyncCount("Pending sync count"),
    ChargerConnected("Charger connected"),
    CameraPermissionGranted("Camera permission granted"),
    KioskModeRecommended("Kiosk mode recommended"),
    LastSuccessfulHeartbeat("Last successful heartbeat"),
    LastAttendanceSync("Last successful attendance sync"),
    LastEmbeddingSync("Last embedding sync"),
}
