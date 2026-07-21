package com.schoollog.attendance.debugqa.presentation

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.debugqa.data.TfliteModelSmokeTester
import com.schoollog.attendance.debugqa.domain.CalibrationFaceCondition
import com.schoollog.attendance.debugqa.domain.CalibrationLightingCondition

@Composable
fun RecognitionQaScreen(onBack: () -> Unit) {
    if (!BuildConfig.DEBUG) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "Recognition QA is available only in debug builds.",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "Back")
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val appContainer = (context.applicationContext as SchoollogAttendanceApp).appContainer
    val viewModel: RecognitionQaViewModel = viewModel(
        factory = RecognitionQaViewModel.factory(
            faceEmbeddingRepository = appContainer.faceEmbeddingRepository,
            settingsRepository = appContainer.settingsRepository,
            recognitionQaRepository = appContainer.recognitionQaRepository,
            recognitionCalibrationLogRepository = appContainer.recognitionCalibrationLogRepository,
            modelSmokeTester = TfliteModelSmokeTester(context.applicationContext),
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClearEmbeddings by remember { mutableStateOf(false) }

    RecognitionQaContent(
        uiState = uiState,
        onReloadCache = viewModel::reloadEmbeddingCache,
        onClearAttendanceEvents = viewModel::clearLocalAttendanceEvents,
        onClearEmbeddings = { confirmClearEmbeddings = true },
        onRunModelSmokeTest = viewModel::runModelSmokeTest,
        onStartNewCalibrationSession = viewModel::startNewCalibrationSession,
        onTesterNameChanged = viewModel::onTesterNameChanged,
        onSessionNotesChanged = viewModel::onSessionNotesChanged,
        onExpectedStudentIdChanged = viewModel::onExpectedStudentIdChanged,
        onLightingConditionChanged = viewModel::onLightingConditionChanged,
        onFaceConditionChanged = viewModel::onFaceConditionChanged,
        onExportCalibrationCsv = {
            viewModel.exportRecognitionCalibrationCsv { csv ->
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Schoollog Recognition Calibration Logs")
                    putExtra(Intent.EXTRA_TEXT, csv)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Export calibration CSV"))
            }
        },
        onExportLogs = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Schoollog Recognition QA")
                putExtra(Intent.EXTRA_TEXT, viewModel.anonymizedPerformanceLog())
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export QA logs"))
        },
        onRefresh = viewModel::refreshEmbeddingSummary,
        onBack = onBack,
    )

    if (confirmClearEmbeddings) {
        AlertDialog(
            onDismissRequest = { confirmClearEmbeddings = false },
            title = { Text(text = "Clear local embeddings?") },
            text = {
                Text(
                    text = "This debug action removes local enrolled embeddings for the current school. It does not export face images or raw embeddings.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearEmbeddings = false
                        viewModel.clearLocalEmbeddings()
                    },
                ) {
                    Text(text = "Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearEmbeddings = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun RecognitionQaContent(
    uiState: RecognitionQaUiState,
    onReloadCache: () -> Unit,
    onClearAttendanceEvents: () -> Unit,
    onClearEmbeddings: () -> Unit,
    onRunModelSmokeTest: () -> Unit,
    onStartNewCalibrationSession: () -> Unit,
    onTesterNameChanged: (String) -> Unit,
    onSessionNotesChanged: (String) -> Unit,
    onExpectedStudentIdChanged: (String) -> Unit,
    onLightingConditionChanged: (CalibrationLightingCondition) -> Unit,
    onFaceConditionChanged: (CalibrationFaceCondition) -> Unit,
    onExportCalibrationCsv: () -> Unit,
    onExportLogs: () -> Unit,
    onRefresh: () -> Unit,
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
                text = "Recognition QA",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Debug build only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            QaMetric(label = "Enrolled students", value = uiState.enrolledStudentCount.toString())
            QaMetric(label = "Model version", value = uiState.modelVersion)
            QaMetric(label = "Embedding size", value = uiState.embeddingSize.toString())
            QaMetric(label = "Model status", value = uiState.modelStatusText())
            QaMetric(label = "Model loaded", value = uiState.modelLoaded?.toString() ?: "not tested")
            QaMetric(label = "Configured input", value = uiState.configuredInputText())
            QaMetric(label = "Input shape", value = uiState.actualInputShape)
            QaMetric(label = "Output shape", value = uiState.actualOutputShape)
            QaMetric(label = "Output data type", value = uiState.actualOutputDataType)
            QaMetric(label = "Average inference time", value = uiState.lastInferenceTimeText())
            QaMetric(label = "Last model error", value = uiState.lastModelError ?: "None")
            QaMetric(label = "Threshold mode", value = uiState.recognitionMode.name)
            QaMetric(
                label = "Thresholds",
                value = "score ${uiState.thresholdAcceptance.formatScore()}, margin ${uiState.thresholdMargin.formatScore()}",
            )
            QaMetric(label = "Average matching time", value = "${uiState.averageMatchingMillis.formatMetric()} ms")
            QaMetric(label = "Last top 3 scores", value = uiState.lastTopMatchScores.formatScores())
            QaMetric(label = "Last failure reason", value = uiState.lastFailureReason)
            Text(
                text = "Calibration session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            QaMetric(label = "Session ID", value = uiState.sessionId.take(8).ifBlank { "--" })
            OutlinedTextField(
                value = uiState.testerName,
                onValueChange = onTesterNameChanged,
                label = { Text(text = "Tester name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.sessionNotes,
                onValueChange = onSessionNotesChanged,
                label = { Text(text = "Session notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onStartNewCalibrationSession, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Start New Test Session")
            }
            OutlinedTextField(
                value = uiState.expectedStudentId,
                onValueChange = onExpectedStudentIdChanged,
                label = { Text(text = "Expected Student ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EnumSelectorRow(
                label = "Lighting",
                values = CalibrationLightingCondition.entries,
                selected = uiState.lightingCondition,
                onSelected = onLightingConditionChanged,
            )
            EnumSelectorRow(
                label = "Face condition",
                values = CalibrationFaceCondition.entries,
                selected = uiState.faceCondition,
                onSelected = onFaceConditionChanged,
            )
            Text(
                text = "Any wrong accepted match means thresholds must be tightened.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Calibration summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            QaMetric(label = "Genuine accepted", value = uiState.genuineAccepted.toString())
            QaMetric(label = "Genuine rejected", value = uiState.genuineRejected.toString())
            QaMetric(label = "Wrong accepted", value = uiState.wrongAccepted.toString())
            QaMetric(label = "Ambiguous rejected", value = uiState.ambiguousRejected.toString())
            QaMetric(label = "Low confidence rejected", value = uiState.lowConfidenceRejected.toString())
            QaMetric(label = "Average decision time", value = "${uiState.averageDecisionTimeMs.formatMetric()} ms")
            Text(
                text = "Last recognition details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            QaMetric(label = "Top 1 student", value = uiState.lastTop1StudentId ?: "--")
            QaMetric(label = "Top 1 score", value = uiState.lastTop1Score.formatNullableScore())
            QaMetric(label = "Top 2 student", value = uiState.lastTop2StudentId ?: "--")
            QaMetric(label = "Top 2 score", value = uiState.lastTop2Score.formatNullableScore())
            QaMetric(label = "Top 3 student", value = uiState.lastTop3StudentId ?: "--")
            QaMetric(label = "Top 3 score", value = uiState.lastTop3Score.formatNullableScore())
            QaMetric(label = "Top1-top2 margin", value = uiState.lastTop1Top2Margin.formatNullableScore())
            QaMetric(label = "Final decision", value = uiState.lastDecision)
            QaMetric(label = "Total decision time", value = "${uiState.lastTotalDecisionTimeMs.formatMetric()} ms")
            QaMetric(label = "Failure reason", value = uiState.lastCalibrationFailureReason)
            QaMetric(label = "Cache version", value = uiState.cacheVersion.toString())
            uiState.statusMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (uiState.isLoading) "Refreshing..." else "Refresh QA Stats")
            }
            OutlinedButton(onClick = onRunModelSmokeTest, modifier = Modifier.fillMaxWidth()) {
                Text(text = if (uiState.isRunningModelSmokeTest) "Running Model Smoke Test..." else "Run Model Smoke Test")
            }
            OutlinedButton(onClick = onReloadCache, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Reload Embedding Cache")
            }
            OutlinedButton(onClick = onClearAttendanceEvents, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Clear Local Attendance Events")
            }
            OutlinedButton(onClick = onClearEmbeddings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Clear Local Embeddings")
            }
            OutlinedButton(onClick = onExportCalibrationCsv, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Export Recognition Calibration Logs")
            }
            OutlinedButton(onClick = onExportLogs, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Export Anonymized Performance Logs")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Back")
            }
            Text(
                text = "Exports never include face images or raw embeddings.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


@Composable
private fun <T : Enum<T>> EnumSelectorRow(
    label: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        values.chunked(2).forEach { rowValues ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowValues.forEach { value ->
                    OutlinedButton(onClick = { onSelected(value) }, modifier = Modifier.weight(1f)) {
                        Text(text = if (value == selected) "${value.name} *" else value.name)
                    }
                }
                if (rowValues.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun QaMetric(
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

private fun Double.formatMetric(): String = String.format("%.2f", this)

private fun Float.formatScore(): String = String.format("%.3f", this)

private fun Float?.formatNullableScore(): String = this?.formatScore() ?: "--"

private fun List<Float>.formatScores(): String =
    if (isEmpty()) "--" else joinToString { String.format("%.3f", it) }


private fun RecognitionQaUiState.modelStatusText(): String =
    when {
        modelSmokeTestPassed == true -> "model found / smoke test passed"
        modelLoaded == true -> "model loaded"
        modelFound == true -> "model found"
        modelFound == false -> "model not found"
        else -> "not tested"
    }

private fun RecognitionQaUiState.configuredInputText(): String =
    if (inputWidth > 0 && inputHeight > 0 && inputChannels > 0) {
        "$inputWidth x $inputHeight x $inputChannels $inputDataType"
    } else {
        "--"
    }

private fun RecognitionQaUiState.lastInferenceTimeText(): String =
    lastModelInferenceMillis?.let { "${it.formatMetric()} ms" } ?: "--"
