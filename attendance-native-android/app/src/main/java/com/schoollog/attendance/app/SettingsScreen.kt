package com.schoollog.attendance.app

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.sync.data.AttendanceSyncScheduler
import com.schoollog.attendance.sync.domain.SyncRunStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SchoollogAttendanceApp
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = app.appContainer.settingsRepository,
            deviceBindingRepository = app.appContainer.deviceBindingRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncStatus by app.appContainer.attendanceSyncRepository.lastSyncStatus
        .collectAsStateWithLifecycle()
    val embeddingSyncStatus by app.appContainer.embeddingSyncRepository.lastEmbeddingSyncStatus
        .collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        syncStatus = syncStatus,
        embeddingSyncStatus = embeddingSyncStatus,
        onSchoolStartTimeChanged = viewModel::onSchoolStartTimeChanged,
        onLateAfterTimeChanged = viewModel::onLateAfterTimeChanged,
        onHalfDayAfterTimeChanged = viewModel::onHalfDayAfterTimeChanged,
        onDuplicateCooldownChanged = viewModel::onDuplicateCooldownChanged,
        onRequireOutTimeChanged = viewModel::onRequireOutTimeChanged,
        onRecognitionModeChanged = viewModel::onRecognitionModeChanged,
        onShowDebugMetricsPanelChanged = viewModel::onShowDebugMetricsPanelChanged,
        onAllowDebugMockRecognitionChanged = viewModel::onAllowDebugMockRecognitionChanged,
        onSaveSettings = viewModel::saveSettings,
        onSyncNow = { AttendanceSyncScheduler.syncNow(context) },
        onSyncEmbeddingsNow = { AttendanceSyncScheduler.syncEmbeddingsNow(context) },
        onUnbindDevice = viewModel::unbindDeviceDebugOnly,
        onBack = onBack,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    syncStatus: SyncRunStatus,
    embeddingSyncStatus: SyncRunStatus,
    onSchoolStartTimeChanged: (String) -> Unit,
    onLateAfterTimeChanged: (String) -> Unit,
    onHalfDayAfterTimeChanged: (String) -> Unit,
    onDuplicateCooldownChanged: (String) -> Unit,
    onRequireOutTimeChanged: (Boolean) -> Unit,
    onRecognitionModeChanged: (RecognitionMode) -> Unit,
    onShowDebugMetricsPanelChanged: (Boolean) -> Unit,
    onAllowDebugMockRecognitionChanged: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onSyncEmbeddingsNow: () -> Unit,
    onUnbindDevice: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "Attendance Rules",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            BindingSummary(uiState = uiState)
            TimeFields(
                uiState = uiState,
                onSchoolStartTimeChanged = onSchoolStartTimeChanged,
                onLateAfterTimeChanged = onLateAfterTimeChanged,
                onHalfDayAfterTimeChanged = onHalfDayAfterTimeChanged,
            )
            OutlinedTextField(
                value = uiState.duplicateScanCooldownMinutes,
                onValueChange = onDuplicateCooldownChanged,
                label = { Text("Duplicate scan cooldown minutes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Require out-time",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.requireOutTime,
                    onCheckedChange = onRequireOutTimeChanged,
                )
            }
            RecognitionModeSelector(
                selectedMode = uiState.recognitionMode,
                onRecognitionModeChanged = onRecognitionModeChanged,
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Show debug metrics",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.showDebugMetricsPanel,
                    onCheckedChange = onShowDebugMetricsPanelChanged,
                )
            }
            if (BuildConfig.DEBUG) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Allow debug mock recognition",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = uiState.allowDebugMockRecognition,
                        onCheckedChange = onAllowDebugMockRecognitionChanged,
                    )
                }
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = onUnbindDevice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Unbind Device")
                }
            }
            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message == "Settings saved") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Medium,
                )
            }
            Button(
                onClick = onSaveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Save settings")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sync: ${syncStatus.displayText()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Sync Now")
            }
            Text(
                text = "Student/embedding sync: ${embeddingSyncStatus.displayText()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (embeddingSyncStatus.isModelMismatch()) {
                Text(
                    text = "Model/embedding version mismatch",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
            OutlinedButton(
                onClick = onSyncEmbeddingsNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Sync Students/Embeddings Now")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Back")
            }
        }
    }
}

@Composable
private fun BindingSummary(uiState: SettingsUiState) {
    Text(
        text = "Device Binding",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    SettingValue(label = "School", value = uiState.schoolName?.let { "$it (${uiState.schoolId})" } ?: uiState.schoolId)
    SettingValue(label = "Device ID", value = uiState.deviceId)
    SettingValue(label = "Gate ID", value = uiState.gateId)
    SettingValue(label = "Device name", value = uiState.deviceName.ifBlank { "--" })
    SettingValue(label = "Last heartbeat", value = uiState.lastHeartbeatAtMillis?.formatTime() ?: "Never")
    SettingValue(label = "App version", value = BuildConfig.VERSION_NAME)
    SettingValue(label = "Model version", value = ModelMetadata.DefaultFaceEmbedding.modelVersion)
    SettingValue(label = "Config version", value = uiState.configVersion.toString())
    SettingValue(label = "Embedding sync version", value = uiState.embeddingSyncVersion.toString())
}

@Composable
private fun SettingValue(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TimeFields(
    uiState: SettingsUiState,
    onSchoolStartTimeChanged: (String) -> Unit,
    onLateAfterTimeChanged: (String) -> Unit,
    onHalfDayAfterTimeChanged: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.schoolStartTime,
            onValueChange = onSchoolStartTimeChanged,
            label = { Text("Start") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = uiState.lateAfterTime,
            onValueChange = onLateAfterTimeChanged,
            label = { Text("Late") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = uiState.halfDayAfterTime,
            onValueChange = onHalfDayAfterTimeChanged,
            label = { Text("Half day") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecognitionModeSelector(
    selectedMode: RecognitionMode,
    onRecognitionModeChanged: (RecognitionMode) -> Unit,
) {
    Text(
        text = "Recognition mode",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecognitionMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onRecognitionModeChanged(mode) },
                label = { Text(mode.name) },
            )
        }
    }
}

private fun SyncRunStatus.displayText(): String =
    when (this) {
        SyncRunStatus.Idle -> "Idle"
        SyncRunStatus.Running -> "Running"
        is SyncRunStatus.Success -> if (duplicateCount > 0) {
            "Synced $syncedCount event(s), $duplicateCount duplicate(s) accepted at ${completedAtMillis.formatTime()}"
        } else {
            "Synced $syncedCount event(s) at ${completedAtMillis.formatTime()}"
        }
        is SyncRunStatus.Failed -> "Failed: $message"
    }

private fun SyncRunStatus.isModelMismatch(): Boolean =
    this is SyncRunStatus.Failed && message.contains("Model/embedding version mismatch", ignoreCase = true)

private fun Long.formatTime(): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(this))
