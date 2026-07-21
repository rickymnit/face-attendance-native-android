package com.schoollog.attendance.debugqa.presentation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.camera.domain.PerformanceMetrics
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun AndroidStressTestScreen(onBack: () -> Unit) {
    if (!BuildConfig.DEBUG) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
                Text(text = "Stress testing is available only in debug builds.")
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
            }
        }
        return
    }

    val context = LocalContext.current
    val appContainer = (context.applicationContext as SchoollogAttendanceApp).appContainer
    val metrics by PerformanceMonitor.metrics.collectAsStateWithLifecycle()
    val pendingSyncCount by appContainer.attendanceEventRepository.pendingSyncCount.collectAsStateWithLifecycle(initialValue = 0)
    val samples = remember { mutableStateListOf<StressSample>() }
    var isRunning by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var remainingMillis by remember { mutableStateOf(StressDurationMillis) }
    var baselineMemory by remember { mutableStateOf(0.0) }
    var baselinePending by remember { mutableStateOf(0) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            val now = System.currentTimeMillis()
            val sample = StressSample.capture(
                context = context,
                metrics = metrics,
                pendingSyncCount = pendingSyncCount,
                startedAtMillis = startedAt,
                baselineMemoryMb = baselineMemory,
                baselinePendingSync = baselinePending,
            )
            samples += sample
            remainingMillis = (StressDurationMillis - (now - startedAt)).coerceAtLeast(0L)
            if (remainingMillis == 0L) isRunning = false
            delay(SampleIntervalMillis)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(text = "Android Stress Test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(text = "Debug build only", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            StressMetric("Status", if (isRunning) "Running" else "Idle")
            StressMetric("Remaining", remainingMillis.formatDuration())
            StressMetric("Samples", samples.size.toString())
            StressMetric("Analyzer FPS", metrics.analyzerInputFps.formatMetric())
            StressMetric("Processed FPS", metrics.processedFps.formatMetric())
            StressMetric("Dropped frames", metrics.droppedFrameCount.toString())
            StressMetric("Average decision", "${metrics.averagePipelineDecisionMillis.formatMetric()} ms")
            StressMetric("Memory", "${StressSample.usedMemoryMb().formatMetric()} MB")
            StressMetric("Pending sync", pendingSyncCount.toString())
            StressMetric("Battery", "${context.batteryPercent()}%")
            StressMetric("Thermal", context.thermalStatusText())
            samples.lastOrNull()?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
                Text(text = "Warnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                warnings.forEach { warning -> Text(text = warning, color = MaterialTheme.colorScheme.error) }
            }
            Button(
                onClick = {
                    samples.clear()
                    startedAt = System.currentTimeMillis()
                    remainingMillis = StressDurationMillis
                    baselineMemory = StressSample.usedMemoryMb()
                    baselinePending = pendingSyncCount
                    isRunning = true
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run 30-Minute Stress Test") }
            OutlinedButton(
                onClick = { isRunning = false },
                enabled = isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop Stress Test") }
            OutlinedButton(
                onClick = { context.exportStressCsv(samples.toList()) },
                enabled = samples.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export Stress Test CSV") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "CSV exports do not include face images or raw embeddings.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StressMetric(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private data class StressSample(
    val timestampMillis: Long,
    val elapsedMillis: Long,
    val analyzerFps: Double,
    val processedFps: Double,
    val droppedFrames: Long,
    val averageDecisionMillis: Double,
    val memoryUsedMb: Double,
    val pendingSyncCount: Int,
    val batteryPercent: Int,
    val thermalStatus: String,
    val warnings: List<String>,
) {
    fun toCsvRow(): String = listOf(
        timestampMillis,
        elapsedMillis,
        analyzerFps,
        processedFps,
        droppedFrames,
        averageDecisionMillis,
        memoryUsedMb,
        pendingSyncCount,
        batteryPercent,
        thermalStatus,
        warnings.joinToString(separator = " | "),
    ).joinToString(separator = ",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    companion object {
        fun capture(
            context: Context,
            metrics: PerformanceMetrics,
            pendingSyncCount: Int,
            startedAtMillis: Long,
            baselineMemoryMb: Double,
            baselinePendingSync: Int,
        ): StressSample {
            val memory = usedMemoryMb()
            val battery = context.batteryPercent()
            val thermal = context.thermalStatusText()
            val warnings = buildList {
                if (metrics.analyzerInputFps > 0.0 && metrics.analyzerInputFps < 8.0) add("Analyzer FPS collapsed")
                if (metrics.averagePipelineDecisionMillis > 3000.0) add("Decision time exceeds 3 seconds")
                if (thermal.contains("SEVERE") || thermal.contains("CRITICAL") || thermal.contains("EMERGENCY")) add("Device thermal warning")
                if (memory - baselineMemoryMb > 150.0) add("Memory usage keeps growing")
                if (pendingSyncCount - baselinePendingSync > 100) add("Pending sync queue growing")
                if (battery < 20) add("Battery low")
            }
            val now = System.currentTimeMillis()
            return StressSample(
                timestampMillis = now,
                elapsedMillis = (now - startedAtMillis).coerceAtLeast(0L),
                analyzerFps = metrics.analyzerInputFps,
                processedFps = metrics.processedFps,
                droppedFrames = metrics.droppedFrameCount,
                averageDecisionMillis = metrics.averagePipelineDecisionMillis,
                memoryUsedMb = memory,
                pendingSyncCount = pendingSyncCount,
                batteryPercent = battery,
                thermalStatus = thermal,
                warnings = warnings,
            )
        }

        fun usedMemoryMb(): Double {
            val runtime = Runtime.getRuntime()
            return (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        }
    }
}

private fun Context.exportStressCsv(samples: List<StressSample>) {
    val csv = buildString {
        appendLine("timestampMillis,elapsedMillis,analyzerFps,processedFps,droppedFrames,averageDecisionMillis,memoryUsedMb,pendingSyncCount,batteryPercent,thermalStatus,warnings")
        samples.forEach { appendLine(it.toCsvRow()) }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "Schoollog Android Stress Test")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    startActivity(Intent.createChooser(sendIntent, "Export stress test CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.batteryPercent(): Int {
    val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) (level * 100) / scale else 100
}

private fun Context.thermalStatusText(): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "UNAVAILABLE"
    val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return when (manager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN"
    }
}

private fun Long.formatDuration(): String {
    val seconds = this / 1000L
    val minutes = seconds / 60L
    return String.format("%02d:%02d", minutes, seconds % 60L)
}

private fun Double.formatMetric(): String = String.format("%.2f", this)

private const val StressDurationMillis = 30L * 60L * 1000L
private const val SampleIntervalMillis = 5L * 1000L
