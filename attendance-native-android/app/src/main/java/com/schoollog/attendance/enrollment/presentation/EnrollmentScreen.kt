package com.schoollog.attendance.enrollment.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schoollog.attendance.app.SchoollogAttendanceApp
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.presentation.FaceBoundingBoxOverlay
import com.schoollog.attendance.core.permissions.CameraPermissionHandler
import com.schoollog.attendance.enrollment.domain.EnrollmentProfile
import com.schoollog.attendance.ml.face.FaceDetectionResult

@Composable
fun EnrollmentScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as SchoollogAttendanceApp).appContainer
    val viewModel: EnrollmentViewModel = viewModel(
        factory = EnrollmentViewModel.factory(
            enrollmentRepository = appContainer.enrollmentRepository,
            settingsRepository = appContainer.settingsRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EnrollmentContent(
        uiState = uiState,
        onStudentIdChanged = viewModel::onStudentIdChanged,
        onNameChanged = viewModel::onNameChanged,
        onClassChanged = viewModel::onClassChanged,
        onSectionChanged = viewModel::onSectionChanged,
        onRollNumberChanged = viewModel::onRollNumberChanged,
        onSearchExistingStudent = viewModel::searchExistingStudent,
        onStartFaceEnrollment = viewModel::startFaceEnrollment,
        onStopFaceEnrollment = viewModel::stopFaceEnrollment,
        onToggleReEnrollment = viewModel::toggleReEnrollment,
        onSaveEnrollment = viewModel::saveEnrollment,
        onLiveEnrollmentOutput = viewModel::onLiveEnrollmentOutput,
        onBack = onBack,
    )
}

@Composable
private fun EnrollmentContent(
    uiState: EnrollmentUiState,
    onStudentIdChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onClassChanged: (String) -> Unit,
    onSectionChanged: (String) -> Unit,
    onRollNumberChanged: (String) -> Unit,
    onSearchExistingStudent: () -> Unit,
    onStartFaceEnrollment: () -> Unit,
    onStopFaceEnrollment: () -> Unit,
    onToggleReEnrollment: () -> Unit,
    onSaveEnrollment: () -> Unit,
    onLiveEnrollmentOutput: (com.schoollog.attendance.camera.domain.LiveFramePipelineOutput) -> Unit,
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
                text = "Student Enrollment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            EnrollmentFormFields(
                uiState = uiState,
                onStudentIdChanged = onStudentIdChanged,
                onNameChanged = onNameChanged,
                onClassChanged = onClassChanged,
                onSectionChanged = onSectionChanged,
                onRollNumberChanged = onRollNumberChanged,
            )
            ActionButtons(
                isEnrollmentCameraActive = uiState.isEnrollmentCameraActive,
                allowReEnrollment = uiState.allowReEnrollment,
                onSearchExistingStudent = onSearchExistingStudent,
                onStartFaceEnrollment = onStartFaceEnrollment,
                onStopFaceEnrollment = onStopFaceEnrollment,
                onToggleReEnrollment = onToggleReEnrollment,
                onSaveEnrollment = onSaveEnrollment,
                onBack = onBack,
            )
            SampleProgress(uiState = uiState)
            if (uiState.isEnrollmentCameraActive) {
                LiveEnrollmentCameraPanel(
                    uiState = uiState,
                    onLiveEnrollmentOutput = onLiveEnrollmentOutput,
                )
            }
            uiState.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (uiState.isSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            StudentListPreview(students = uiState.students)
        }
    }
}

@Composable
private fun EnrollmentFormFields(
    uiState: EnrollmentUiState,
    onStudentIdChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onClassChanged: (String) -> Unit,
    onSectionChanged: (String) -> Unit,
    onRollNumberChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.studentId,
        onValueChange = onStudentIdChanged,
        label = { Text("ERP Student ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = uiState.className,
            onValueChange = onClassChanged,
            label = { Text("Class") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = uiState.section,
            onValueChange = onSectionChanged,
            label = { Text("Section") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = uiState.rollNumber,
        onValueChange = onRollNumberChanged,
        label = { Text("Roll number") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ActionButtons(
    isEnrollmentCameraActive: Boolean,
    allowReEnrollment: Boolean,
    onSearchExistingStudent: () -> Unit,
    onStartFaceEnrollment: () -> Unit,
    onStopFaceEnrollment: () -> Unit,
    onToggleReEnrollment: () -> Unit,
    onSaveEnrollment: () -> Unit,
    onBack: () -> Unit,
) {
    OutlinedButton(
        onClick = onSearchExistingStudent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Search existing student")
    }
    OutlinedButton(
        onClick = onToggleReEnrollment,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = if (allowReEnrollment) "Re-enroll enabled" else "Enable Re-enroll")
    }
    Button(
        onClick = if (isEnrollmentCameraActive) onStopFaceEnrollment else onStartFaceEnrollment,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = if (isEnrollmentCameraActive) "Stop face enrollment" else "Start face enrollment")
    }
    OutlinedButton(
        onClick = onSaveEnrollment,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Save student details only")
    }
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Back")
    }
}

@Composable
private fun SampleProgress(uiState: EnrollmentUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Live samples: ${uiState.capturedSampleCount}/3",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        uiState.sampleStatuses.forEachIndexed { index, status ->
            Text(
                text = "Sample ${index + 1}: $status",
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.contains("captured", ignoreCase = true)) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            text = uiState.liveEnrollmentMessage,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LiveEnrollmentCameraPanel(
    uiState: EnrollmentUiState,
    onLiveEnrollmentOutput: (com.schoollog.attendance.camera.domain.LiveFramePipelineOutput) -> Unit,
) {
    CameraPermissionHandler { permissionState ->
        var cameraStatus by remember { mutableStateOf("Waiting for camera permission") }
        var analyzerFps by remember { mutableDoubleStateOf(0.0) }
        var lastFrameInfo by remember { mutableStateOf<CameraFrameInfo?>(null) }
        var lastFaceDetectionResult by remember { mutableStateOf<FaceDetectionResult?>(null) }

        if (!permissionState.isGranted) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Camera permission required for live enrollment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = permissionState.requestPermission) {
                    Text(text = "Allow camera")
                }
            }
            return@CameraPermissionHandler
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color.Black),
        ) {
            EnrollmentCameraPreview(
                onFrameProcessed = { frameInfo, output, fps ->
                    lastFrameInfo = frameInfo
                    lastFaceDetectionResult = output.faceDetectionResult
                    analyzerFps = fps
                    onLiveEnrollmentOutput(output)
                },
                onCameraStatusChanged = { cameraStatus = it },
                modifier = Modifier.fillMaxSize(),
            )
            FaceBoundingBoxOverlay(
                frameInfo = lastFrameInfo,
                faceDetectionResult = lastFaceDetectionResult,
                modifier = Modifier.fillMaxSize(),
                mirrorHorizontally = true,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(12.dp),
            ) {
                Text(
                    text = cameraStatus,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = uiState.liveEnrollmentMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Analyzer FPS: ${String.format("%.1f", analyzerFps)}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StudentListPreview(students: List<EnrollmentProfile>) {
    Text(
        text = "Enrolled students",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (students.isEmpty()) {
        Text(
            text = "No students enrolled yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    students.forEach { student ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = student.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "ID ${student.studentId} | Class ${student.className}-${student.section} | Roll ${student.rollNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
