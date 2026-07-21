package com.schoollog.attendance.camera.presentation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.attendance.domain.GateModeState
import com.schoollog.attendance.attendance.presentation.GateAttendanceUiState
import com.schoollog.attendance.attendance.presentation.GateAttendanceViewModel
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.BenchmarkMetricSummary
import com.schoollog.attendance.camera.domain.PerformanceMetrics
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import com.schoollog.attendance.camera.domain.PipelineBenchmarkState
import com.schoollog.attendance.camera.domain.StableFaceTrackingResult
import com.schoollog.attendance.camera.domain.StableFaceTrackerState
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.DeviceBinding
import com.schoollog.attendance.core.permissions.CameraPermissionHandler
import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.liveness.LivenessDecision
import com.schoollog.attendance.ml.liveness.LivenessResult
import com.schoollog.attendance.sync.domain.SyncRunStatus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun GateCameraScreen(onBack: () -> Unit) {
    BackHandler(enabled = true) { /* Gate Mode exits only through the confirmed on-screen action. */ }
    GateModeWindowPolicy()

    val context = LocalContext.current
    val appContainer = (context.applicationContext as SchoollogAttendanceApp).appContainer
    val syncStatus by appContainer.attendanceSyncRepository.lastSyncStatus.collectAsStateWithLifecycle()
    val attendanceRules by appContainer.settingsRepository.attendanceRules.collectAsStateWithLifecycle(
        initialValue = AttendanceRules(),
    )
    val deviceBinding by appContainer.deviceBindingRepository.deviceBinding.collectAsStateWithLifecycle(initialValue = null)
    val performanceMetrics by PerformanceMonitor.metrics.collectAsStateWithLifecycle()
    val benchmarkState by PerformanceMonitor.benchmarkState.collectAsStateWithLifecycle()
    val networkStatus by rememberNetworkStatus()
    val batteryStatus by rememberBatteryStatus()
    val currentTimeMillis by rememberCurrentTimeMillis()
    val gateAttendanceViewModel: GateAttendanceViewModel = viewModel(
        factory = GateAttendanceViewModel.factory(
            attendanceEventRepository = appContainer.attendanceEventRepository,
            studentRepository = appContainer.studentRepository,
            failedRecognitionRepository = appContainer.failedRecognitionRepository,
            settingsRepository = appContainer.settingsRepository,
            recognitionCalibrationLogRepository = appContainer.recognitionCalibrationLogRepository,
        ),
    )
    val gateUiState by gateAttendanceViewModel.uiState.collectAsStateWithLifecycle()

    CameraPermissionHandler { permissionState ->
        var cameraStatus by remember { mutableStateOf("Waiting for camera permission") }
        var lastFrameInfo by remember { mutableStateOf<CameraFrameInfo?>(null) }
        var lastProcessedAtMillis by remember { mutableStateOf<Long?>(null) }
        var lastFaceDetectionResult by remember { mutableStateOf<FaceDetectionResult?>(null) }
        var lastFaceCropResult by remember { mutableStateOf<FaceCropResult?>(null) }
        var lastStableFaceTrackingResult by remember { mutableStateOf<StableFaceTrackingResult?>(null) }
        var lastLivenessResult by remember { mutableStateOf<LivenessResult?>(null) }
        var analyzerFps by remember { mutableDoubleStateOf(0.0) }
        var showExitConfirmation by remember { mutableStateOf(false) }

        LaunchedEffect(benchmarkState.isRunning, benchmarkState.sessionId) {
            if (benchmarkState.isRunning) {
                delay(benchmarkState.durationMillis)
                PerformanceMonitor.finishBenchmark()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (permissionState.isGranted) {
                CameraPreview(
                    onFrameProcessed = { frameInfo, output, fps ->
                        lastFrameInfo = frameInfo
                        lastProcessedAtMillis = output.processedAtMillis
                        lastFaceDetectionResult = output.faceDetectionResult
                        lastFaceCropResult = output.faceCropResult ?: lastFaceCropResult
                        lastStableFaceTrackingResult = output.stableFaceTrackingResult
                        lastLivenessResult = output.livenessResult
                        analyzerFps = fps
                        gateAttendanceViewModel.onPipelineOutput(output)
                    },
                    onCameraStatusChanged = { status -> cameraStatus = status },
                    allowDebugMockRecognition = BuildConfig.DEBUG && attendanceRules.allowDebugMockRecognition,
                    schoolId = attendanceRules.schoolId,
                    recognitionMode = attendanceRules.recognitionMode,
                    faceEmbeddingRepository = appContainer.faceEmbeddingRepository,
                    modifier = Modifier.fillMaxSize(),
                )
                FaceBoundingBoxOverlay(
                    frameInfo = lastFrameInfo,
                    faceDetectionResult = lastFaceDetectionResult,
                    modifier = Modifier.fillMaxSize(),
                    mirrorHorizontally = true,
                )
            } else {
                CameraPermissionRequired(
                    onBack = { showExitConfirmation = true },
                    onRequestPermission = permissionState.requestPermission,
                )
            }

            GateKioskOverlay(
                cameraStatus = cameraStatus,
                lastFrameInfo = lastFrameInfo,
                lastProcessedAtMillis = lastProcessedAtMillis,
                faceDetectionResult = lastFaceDetectionResult,
                faceCropResult = lastFaceCropResult,
                stableFaceTrackingResult = lastStableFaceTrackingResult,
                livenessResult = lastLivenessResult,
                gateUiState = gateUiState,
                syncStatus = syncStatus,
                performanceMetrics = performanceMetrics,
                benchmarkState = benchmarkState,
                showDebugMetricsPanel = BuildConfig.DEBUG && attendanceRules.showDebugMetricsPanel,
                analyzerFps = analyzerFps,
                deviceBinding = deviceBinding,
                networkStatus = networkStatus,
                batteryStatus = batteryStatus,
                currentTimeMillis = currentTimeMillis,
                onStartBenchmark = {
                    PerformanceMonitor.startBenchmark(
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}",
                    )
                },
                onCancelBenchmark = PerformanceMonitor::cancelBenchmark,
                onExportBenchmarkCsv = { context.exportText("schoollog-benchmark.csv", PerformanceMonitor.benchmarkCsv()) },
                onExitRequested = { showExitConfirmation = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text(text = "Exit Gate Mode?") },
                text = { Text(text = "Gate Mode is intended to stay active during school entry. Exit only for setup, support, or admin work.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmation = false
                            onBack()
                        },
                    ) {
                        Text(text = "Exit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmation = false }) {
                        Text(text = "Stay")
                    }
                },
            )
        }
    }
}

@Composable
private fun GateModeWindowPolicy() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }
}

@Composable
private fun CameraPermissionRequired(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Camera permission required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = "Allow camera")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(text = "Exit Gate Mode")
            }
        }
    }
}

@Composable
private fun GateKioskOverlay(
    cameraStatus: String,
    lastFrameInfo: CameraFrameInfo?,
    lastProcessedAtMillis: Long?,
    faceDetectionResult: FaceDetectionResult?,
    faceCropResult: FaceCropResult?,
    stableFaceTrackingResult: StableFaceTrackingResult?,
    livenessResult: LivenessResult?,
    gateUiState: GateAttendanceUiState,
    syncStatus: SyncRunStatus,
    performanceMetrics: PerformanceMetrics,
    benchmarkState: PipelineBenchmarkState,
    showDebugMetricsPanel: Boolean,
    analyzerFps: Double,
    deviceBinding: DeviceBinding?,
    networkStatus: NetworkStatus,
    batteryStatus: BatteryStatus,
    currentTimeMillis: Long,
    onStartBenchmark: () -> Unit,
    onCancelBenchmark: () -> Unit,
    onExportBenchmarkCsv: () -> Unit,
    onExitRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        KioskHeader(
            deviceBinding = deviceBinding,
            networkStatus = networkStatus,
            currentTimeMillis = currentTimeMillis,
            pendingSyncCount = gateUiState.pendingSyncCount,
            onExitRequested = onExitRequested,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
        WarningStack(
            networkStatus = networkStatus,
            batteryStatus = batteryStatus,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp, start = 16.dp, end = 16.dp),
        )
        ResultPanel(
            gateUiState = gateUiState,
            syncStatus = syncStatus,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(18.dp),
        )
        if (showDebugMetricsPanel) {
            DebugPanel(
                cameraStatus = cameraStatus,
                lastFrameInfo = lastFrameInfo,
                lastProcessedAtMillis = lastProcessedAtMillis,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                stableFaceTrackingResult = stableFaceTrackingResult,
                livenessResult = livenessResult,
                performanceMetrics = performanceMetrics,
                benchmarkState = benchmarkState,
                analyzerFps = analyzerFps,
                onStartBenchmark = onStartBenchmark,
                onCancelBenchmark = onCancelBenchmark,
                onExportBenchmarkCsv = onExportBenchmarkCsv,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun KioskHeader(
    deviceBinding: DeviceBinding?,
    networkStatus: NetworkStatus,
    currentTimeMillis: Long,
    pendingSyncCount: Int,
    onExitRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceBinding?.gateId ?: "Gate Mode",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = deviceBinding?.deviceName?.takeIf { it.isNotBlank() } ?: deviceBinding?.deviceId ?: "Registered gate device",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = currentTimeMillis.formatTime(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${networkStatus.label} | Pending $pendingSyncCount",
                color = if (networkStatus.isOnline) Color.White else WarningColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onExitRequested) {
            Text(text = "Exit")
        }
    }
}

@Composable
private fun WarningStack(
    networkStatus: NetworkStatus,
    batteryStatus: BatteryStatus,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (!networkStatus.isOnline) {
            WarningBanner(text = "Offline mode active, attendance will sync later")
        }
        if (batteryStatus.shouldWarn) {
            WarningBanner(text = "Connect charger")
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Text(
        text = text,
        color = Color.Black,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(WarningColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun ResultPanel(
    gateUiState: GateAttendanceUiState,
    syncStatus: SyncRunStatus,
    modifier: Modifier = Modifier,
) {
    val resultColor = gateUiState.gateState.resultColor()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(resultColor.copy(alpha = 0.90f), RoundedCornerShape(8.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            text = gateUiState.message,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        gateUiState.student?.let { student ->
            Text(
                text = student.name,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Class ${student.className}-${student.section} | Roll ${student.rollNumber} | ${student.attendanceStatus}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(
                text = "Pending sync: ${gateUiState.pendingSyncCount}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = syncStatus.shortText(),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DebugPanel(
    cameraStatus: String,
    lastFrameInfo: CameraFrameInfo?,
    lastProcessedAtMillis: Long?,
    faceDetectionResult: FaceDetectionResult?,
    faceCropResult: FaceCropResult?,
    stableFaceTrackingResult: StableFaceTrackingResult?,
    livenessResult: LivenessResult?,
    performanceMetrics: PerformanceMetrics,
    benchmarkState: PipelineBenchmarkState,
    analyzerFps: Double,
    onStartBenchmark: () -> Unit,
    onCancelBenchmark: () -> Unit,
    onExportBenchmarkCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text = "Debug", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text = "Camera: $cameraStatus", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Face: ${faceDetectionResult.faceStatusText()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Angles: ${faceDetectionResult.faceAngleText()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Box: ${faceDetectionResult.boundingBoxStatusText()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Stability: ${stableFaceTrackingResult.stabilityStatusText()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Liveness: ${livenessResult.livenessStatusText()}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "FPS: ${String.format("%.1f", analyzerFps)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(text = "Last: ${lastProcessedAtMillis?.formatTime() ?: "--"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "Frame: ${lastFrameInfo?.width ?: "--"} x ${lastFrameInfo?.height ?: "--"}, rotation ${lastFrameInfo?.rotationDegrees ?: "--"}",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        FaceCropDebugPreview(faceCropResult = faceCropResult, modifier = Modifier.padding(top = 8.dp))
        PerformanceDebugPanel(metrics = performanceMetrics, modifier = Modifier.padding(top = 8.dp))
        BenchmarkDebugPanel(
            benchmarkState = benchmarkState,
            onStartBenchmark = onStartBenchmark,
            onCancelBenchmark = onCancelBenchmark,
            onExportBenchmarkCsv = onExportBenchmarkCsv,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FaceCropDebugPreview(
    faceCropResult: FaceCropResult?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Face crop: ${faceCropResult.cropStatusText()}",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        val bitmap = faceCropResult?.bitmap
        if (faceCropResult?.isSuccess == true && bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Live face crop preview",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(72.dp),
            )
        }
    }
}

@Composable
private fun PerformanceDebugPanel(
    metrics: PerformanceMetrics,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Input FPS ${metrics.analyzerInputFps.formatMetric()} | Processed FPS ${metrics.processedFps.formatMetric()}",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Face ${metrics.averageFaceDetectionMillis.formatMetric()} ms | Quality ${metrics.averageQualityEvaluationMillis.formatMetric()} ms | Stable ${metrics.averageStableTrackingMillis.formatMetric()} ms",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Live ${metrics.averageLivenessMillis.formatMetric()} ms | Crop ${metrics.averageFaceCropMillis.formatMetric()} ms | Embed ${metrics.averageEmbeddingInferenceMillis.formatMetric()} ms",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Match ${metrics.averageMatchingMillis.formatMetric()} ms | Decision ${metrics.averagePipelineDecisionMillis.formatMetric()} ms",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Dropped ${metrics.droppedFrameCount} | Mem ${metrics.memoryUsedMb.formatMetric()} MB | Last failure: ${metrics.lastFailureReason}",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}


@Composable
private fun BenchmarkDebugPanel(
    benchmarkState: PipelineBenchmarkState,
    onStartBenchmark: () -> Unit,
    onCancelBenchmark: () -> Unit,
    onExportBenchmarkCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Device benchmark",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = benchmarkState.statusMessage,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (benchmarkState.isRunning) {
                OutlinedButton(onClick = onCancelBenchmark) { Text(text = "Cancel") }
            } else {
                OutlinedButton(onClick = onStartBenchmark) { Text(text = "Run 30-second benchmark") }
            }
        }
        benchmarkState.result?.let { result ->
            Text(
                text = "Decision avg ${result.totalDecisionMillis.average.formatMetric()} ms | p95 ${result.totalDecisionMillis.p95.formatMetric()} | worst ${result.totalDecisionMillis.worst.formatMetric()}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Dropped ${result.droppedFrames} | Success ${result.successCount} | Failure ${result.failureCount}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Backlog ${if (result.noAnalyzerBacklog) "none" else "review"} | Target ${if (result.targetMet) "met" else "missed"}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Face p95 ${result.faceDetectionMillis.p95.formatMetric()} | Embed p95 ${result.embeddingInferenceMillis.p95.formatMetric()} | Match p95 ${result.localMatchingMillis.p95.formatMetric()}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onExportBenchmarkCsv, modifier = Modifier.padding(top = 4.dp)) {
                Text(text = "Export benchmark CSV")
            }
        }
    }
}

@Composable
private fun rememberCurrentTimeMillis(): androidx.compose.runtime.State<Long> {
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.longValue = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return currentTime
}

@Composable
private fun rememberNetworkStatus(): androidx.compose.runtime.State<NetworkStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(context.currentNetworkStatus()) }
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                status.value = context.currentNetworkStatus()
            }

            override fun onLost(network: Network) {
                status.value = context.currentNetworkStatus()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                status.value = context.currentNetworkStatus()
            }
        }
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    return status
}

@Composable
private fun rememberBatteryStatus(): androidx.compose.runtime.State<BatteryStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(context.currentBatteryStatus()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                status.value = intent.toBatteryStatus()
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        status.value = context.currentBatteryStatus()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return status
}

private fun Context.currentNetworkStatus(): NetworkStatus {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    val isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    return NetworkStatus(
        isOnline = isOnline,
        label = if (isOnline) "Online" else "Offline",
    )
}

private fun Context.currentBatteryStatus(): BatteryStatus {
    val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return intent?.toBatteryStatus() ?: BatteryStatus(levelPercent = 100, isCharging = true)
}

private fun Intent.toBatteryStatus(): BatteryStatus {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 100
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return BatteryStatus(
        levelPercent = percent,
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0,
    )
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun GateModeState.resultColor(): Color =
    when (this) {
        is GateModeState.AttendanceMarked -> SuccessColor
        is GateModeState.DuplicateScan,
        is GateModeState.ManualReviewRequired -> ErrorColor
        GateModeState.IdleWaitingForFace -> NeutralColor
        GateModeState.FaceDetected,
        GateModeState.FaceQualityChecking,
        GateModeState.HoldStill,
        GateModeState.CheckingLiveness,
        GateModeState.GeneratingEmbeddingPlaceholder,
        GateModeState.MatchingStudentPlaceholder -> ActiveColor
    }

private fun Long.formatTime(): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(this))

private fun SyncRunStatus.shortText(): String =
    when (this) {
        SyncRunStatus.Idle -> "Sync idle"
        SyncRunStatus.Running -> "Syncing"
        is SyncRunStatus.Success -> if (duplicateCount > 0) "Synced $syncedCount, duplicates $duplicateCount" else "Synced $syncedCount"
        is SyncRunStatus.Failed -> "Sync pending"
    }

private fun FaceDetectionResult?.faceStatusText(): String {
    val result = this ?: return "No face detected"
    return when (result.faceCount) {
        0 -> "No face detected"
        1 -> if (result.quality.qualityPassed) "One face detected" else result.quality.reason
        else -> "Multiple faces detected"
    }
}

private fun FaceDetectionResult?.faceAngleText(): String {
    val face = this?.primaryFace ?: return "--"
    return "X ${String.format("%.1f", face.headEulerAngleX)}, " +
        "Y ${String.format("%.1f", face.headEulerAngleY)}, " +
        "Z ${String.format("%.1f", face.headEulerAngleZ)}"
}

private fun FaceDetectionResult?.boundingBoxStatusText(): String {
    val result = this ?: return "--"
    val face = result.primaryFace ?: return result.quality.reason
    return "${result.quality.reason} " +
        "(${face.boundingBox.left.toInt()},${face.boundingBox.top.toInt()} - " +
        "${face.boundingBox.right.toInt()},${face.boundingBox.bottom.toInt()})"
}

private fun StableFaceTrackingResult?.stabilityStatusText(): String {
    val result = this ?: return "Waiting for student..."
    val label = when (result.state) {
        StableFaceTrackerState.WAITING_FOR_FACE -> "Waiting for student..."
        StableFaceTrackerState.FACE_DETECTED -> "Face detected"
        StableFaceTrackerState.HOLD_STILL -> "Hold still..."
        StableFaceTrackerState.FACE_STABLE -> "Face stable"
        StableFaceTrackerState.READY_FOR_LIVENESS -> "Checking liveness..."
    }
    return "$label (${result.stableDurationMillis} ms)"
}

private fun LivenessResult?.livenessStatusText(): String =
    when (this?.decision) {
        LivenessDecision.PASS -> "Liveness passed (${String.format("%.2f", score)})"
        LivenessDecision.FAIL -> "Liveness failed. Please try again"
        LivenessDecision.UNCERTAIN -> "Checking liveness..."
        null -> "Waiting"
    }


private fun Context.exportText(fileName: String, content: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    startActivity(Intent.createChooser(sendIntent, "Export $fileName"))
}

private fun Double.formatMetric(): String = String.format("%.1f", this)

private fun FaceCropResult?.cropStatusText(): String =
    when {
        this == null -> "Waiting"
        isSuccess -> "SUCCESS ${metadata?.normalizedSize ?: 0}px"
        else -> failureReason.name
    }

private data class NetworkStatus(
    val isOnline: Boolean,
    val label: String,
)

private data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
) {
    val shouldWarn: Boolean = levelPercent <= LowBatteryWarningPercent && !isCharging

    private companion object {
        const val LowBatteryWarningPercent = 20
    }
}

private val SuccessColor = Color(0xFF128C4A)
private val ErrorColor = Color(0xFFB3261E)
private val ActiveColor = Color(0xFFC47F00)
private val NeutralColor = Color(0xFF1F2937)
private val WarningColor = Color(0xFFFFC857)
