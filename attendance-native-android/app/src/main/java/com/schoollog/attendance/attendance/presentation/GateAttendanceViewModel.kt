package com.schoollog.attendance.attendance.presentation

import androidx.lifecycle.ViewModel
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.attendance.domain.AttendanceEventDraft
import com.schoollog.attendance.attendance.domain.AttendanceStatusCalculator
import com.schoollog.attendance.attendance.domain.AttendanceEventRepository
import com.schoollog.attendance.attendance.domain.AttendanceGateStateMachine
import com.schoollog.attendance.attendance.domain.FailedRecognitionDraft
import com.schoollog.attendance.attendance.domain.FailedRecognitionRepository
import com.schoollog.attendance.attendance.domain.GateModeEvent
import com.schoollog.attendance.attendance.domain.GateModeState
import com.schoollog.attendance.attendance.domain.StudentAttendanceResult
import com.schoollog.attendance.attendance.domain.StudentRepository
import com.schoollog.attendance.camera.domain.LiveFramePipelineOutput
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogDraft
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogRepository
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationSession
import com.schoollog.attendance.ml.liveness.LivenessDecision
import com.schoollog.attendance.ml.recognition.EmbeddingFailureReason
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GateAttendanceViewModel(
    private val attendanceEventRepository: AttendanceEventRepository,
    private val studentRepository: StudentRepository,
    private val failedRecognitionRepository: FailedRecognitionRepository,
    private val settingsRepository: SettingsRepository,
    private val recognitionCalibrationLogRepository: RecognitionCalibrationLogRepository,
) : ViewModel() {
    private val stateMachine = AttendanceGateStateMachine()
    private val recentMarks = mutableMapOf<String, Long>()
    private val _uiState = MutableStateFlow(GateAttendanceUiState())
    val uiState: StateFlow<GateAttendanceUiState> = _uiState.asStateFlow()
    private var resetJob: Job? = null
    private var saveJob: Job? = null
    private var attendanceRules = AttendanceRules()
    private var stableDecisionStartedAtMillis: Long? = null

    init {
        viewModelScope.launch {
            attendanceEventRepository.pendingSyncCount.collect { pendingCount ->
                _uiState.update { it.copy(pendingSyncCount = pendingCount) }
            }
        }
        viewModelScope.launch {
            settingsRepository.attendanceRules.collect { rules ->
                attendanceRules = rules
            }
        }
    }

    fun onPipelineOutput(output: LiveFramePipelineOutput) {
        if (output.stableFaceTrackingResult?.isReadyForLiveness == true && stableDecisionStartedAtMillis == null) {
            stableDecisionStartedAtMillis = System.currentTimeMillis()
        }
        if (_uiState.value.gateState.isTerminal() || saveJob?.isActive == true) return

        if (stateMachine.currentState == GateModeState.MatchingStudentPlaceholder) {
            handleStudentMatch(output)
            return
        }

        val event = nextEventForCurrentState(output)
        val newState = stateMachine.transition(event)
        publishState(newState, output.userMessage)

        if (newState == GateModeState.MatchingStudentPlaceholder && output.faceMatchResult != null) {
            handleStudentMatch(output)
            return
        }

        if (newState.isTerminal()) {
            if (output.faceMatchResult == null && output.embeddingResult != null) {
                logRecognitionCalibrationAttempt(output)
            }
            logFailureIfNeeded(event, output)
            scheduleReset()
        }
    }

    private fun nextEventForCurrentState(output: LiveFramePipelineOutput): GateModeEvent {
        val state = stateMachine.currentState
        val detection = output.faceDetectionResult
        val hasExactlyOneFace = detection?.hasExactlyOneFace == true
        val qualityPassed = detection?.quality?.passes == true
        val stableReady = output.stableFaceTrackingResult?.isReadyForLiveness == true

        return when (state) {
            GateModeState.IdleWaitingForFace -> {
                if (hasExactlyOneFace) GateModeEvent.FaceDetected else GateModeEvent.FrameReceived
            }
            GateModeState.FaceDetected,
            GateModeState.FaceQualityChecking -> when {
                !hasExactlyOneFace -> GateModeEvent.ResetForNextStudent
                !qualityPassed -> GateModeEvent.FaceQualityFailed(detection?.quality?.reason ?: "Face quality failed")
                else -> GateModeEvent.FaceQualityPassed
            }
            GateModeState.HoldStill -> when {
                !hasExactlyOneFace -> GateModeEvent.ResetForNextStudent
                !qualityPassed -> GateModeEvent.FaceQualityFailed(detection?.quality?.reason ?: "Face quality failed")
                stableReady -> GateModeEvent.FaceStable
                else -> GateModeEvent.FrameReceived
            }
            GateModeState.CheckingLiveness -> when {
                !hasExactlyOneFace -> GateModeEvent.LivenessFailed("Face lost during liveness. Please try again")
                !qualityPassed -> GateModeEvent.LivenessFailed("Face quality failed during liveness. Please try again")
                output.livenessResult?.decision == LivenessDecision.PASS -> GateModeEvent.LivenessPassed
                output.livenessResult?.decision == LivenessDecision.FAIL -> {
                    GateModeEvent.LivenessFailed(output.livenessResult.reason)
                }
                else -> GateModeEvent.FrameReceived
            }
            GateModeState.GeneratingEmbeddingPlaceholder -> when {
                output.embeddingResult?.isSuccess == false -> {
                    GateModeEvent.MatchNotFound(output.embeddingResult.failureReason.embeddingFailureMessage())
                }
                output.recognitionDecision == RecognitionDecision.ERROR -> GateModeEvent.MatchNotFound(output.userMessage)
                output.faceMatchResult != null -> GateModeEvent.EmbeddingGeneratedPlaceholder
                else -> GateModeEvent.FrameReceived
            }
            GateModeState.MatchingStudentPlaceholder -> GateModeEvent.FrameReceived
            is GateModeState.AttendanceMarked,
            is GateModeState.DuplicateScan,
            is GateModeState.ManualReviewRequired -> GateModeEvent.FrameReceived
        }
    }

    private fun handleStudentMatch(output: LiveFramePipelineOutput) {
        val match = output.faceMatchResult
        if (match != null) {
            logRecognitionCalibrationAttempt(output)
        }
        val matchedStudentId = match?.studentId
        if (match?.decision != RecognitionDecision.MATCH_ACCEPTED || matchedStudentId == null) {
            val event = GateModeEvent.MatchNotFound(match?.reason ?: "Recognition did not match a student")
            logFailureIfNeeded(event, output)
            publishTerminal(event)
            return
        }

        saveJob = viewModelScope.launch {
            val studentRecord = studentRepository.findByErpStudentId(attendanceRules.schoolId, matchedStudentId)
            if (studentRecord == null) {
                val event = GateModeEvent.MatchNotFound("Matched student record not found. Manual review required")
                logFailureIfNeeded(event, output)
                publishTerminal(event)
                return@launch
            }
            val attendanceStatus = AttendanceStatusCalculator.statusFor(LocalTime.now(), attendanceRules)
            val student = StudentAttendanceResult(
                studentId = studentRecord.studentId,
                name = studentRecord.name,
                className = studentRecord.className,
                section = studentRecord.section,
                rollNumber = studentRecord.rollNumber,
                attendanceStatus = attendanceStatus,
            )

            if (isDuplicate(student.studentId)) {
                publishTerminal(GateModeEvent.DuplicateFound(student))
                return@launch
            }

            runCatching {
                attendanceEventRepository.saveAttendanceEvent(
                    AttendanceEventDraft(
                        schoolId = attendanceRules.schoolId,
                        erpStudentId = student.studentId,
                        deviceId = attendanceRules.deviceId,
                        gateId = attendanceRules.gateId,
                        eventType = attendanceStatus,
                        attendanceDate = LocalDate.now().toString(),
                        timestampLocal = System.currentTimeMillis(),
                        matchScore = match.score,
                        livenessScore = output.livenessResult?.score ?: 0f,
                        qualityScore = output.faceDetectionResult?.quality?.score ?: 0f,
                    ),
                )
            }.onSuccess {
                recentMarks[student.studentId] = System.currentTimeMillis()
                publishTerminal(GateModeEvent.AttendanceSaved(student))
            }.onFailure {
                val event = GateModeEvent.MatchNotFound("Could not save attendance locally")
                logFailureIfNeeded(event, output)
                publishTerminal(event)
            }
        }
    }


    private fun logRecognitionCalibrationAttempt(output: LiveFramePipelineOutput) {
        if (!BuildConfig.DEBUG) return
        val match = output.faceMatchResult
        if (match == null && output.embeddingResult == null) return

        val top1 = match?.topMatches?.getOrNull(0)
        val top2 = match?.topMatches?.getOrNull(1)
        val top3 = match?.topMatches?.getOrNull(2)
        val decision = match?.decision ?: output.recognitionDecision
        val top1Score = top1?.score ?: match?.bestScore ?: 0f
        val session = RecognitionCalibrationSession.currentState()
        val now = System.currentTimeMillis()
        val totalDecisionTimeMs = stableDecisionStartedAtMillis?.let { (now - it).coerceAtLeast(0L).toDouble() } ?: 0.0
        viewModelScope.launch {
            recognitionCalibrationLogRepository.logAttempt(
                RecognitionCalibrationLogDraft(
                    sessionId = session.sessionId,
                    timestamp = now,
                    expectedStudentId = RecognitionCalibrationSession.currentExpectedStudentId(),
                    predictedStudentId = match?.studentId,
                    top1StudentId = top1?.studentId,
                    top1Score = top1Score,
                    top2StudentId = top2?.studentId,
                    top2Score = top2?.score ?: match?.secondBestScore,
                    top3StudentId = top3?.studentId,
                    top3Score = top3?.score,
                    margin = top2?.let { top1Score - it.score },
                    livenessScore = output.livenessResult?.score ?: 0f,
                    qualityScore = output.faceDetectionResult?.quality?.score ?: 0f,
                    decision = decision,
                    failureReason = (match?.reason ?: output.userMessage).takeIf { decision != RecognitionDecision.MATCH_ACCEPTED },
                    recognitionMode = attendanceRules.recognitionMode,
                    inferenceTimeMs = output.embeddingInferenceTimeMillis ?: 0.0,
                    matchingTimeMs = match?.matchingTimeMillis ?: 0.0,
                    totalDecisionTimeMs = totalDecisionTimeMs,
                    lightingCondition = session.lightingCondition,
                    faceCondition = session.faceCondition,
                    testerName = session.testerName.trim().takeIf { it.isNotEmpty() },
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}",
                    modelVersion = output.embeddingResult?.modelVersion ?: ModelMetadata.DefaultFaceEmbedding.modelVersion,
                    schoolId = attendanceRules.schoolId,
                    sessionNotes = session.notes.trim().takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    private fun publishTerminal(event: GateModeEvent) {
        val newState = stateMachine.transition(event)
        publishState(newState)
        stableDecisionStartedAtMillis = null
        scheduleReset()
    }


    private fun EmbeddingFailureReason.embeddingFailureMessage(): String =
        when (this) {
            EmbeddingFailureReason.MODEL_NOT_FOUND -> "Face model not installed"
            EmbeddingFailureReason.MODEL_LOAD_FAILED -> "Face model failed to load"
            EmbeddingFailureReason.INPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.INPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_SIZE_MISMATCH -> "Face model does not match app configuration"
            EmbeddingFailureReason.INVALID_INPUT -> "Invalid face crop for model"
            EmbeddingFailureReason.INFERENCE_FAILED -> "Face model inference failed"
            EmbeddingFailureReason.INVALID_OUTPUT -> "Face model output invalid"
            EmbeddingFailureReason.SUCCESS -> "Face embedding ready"
        }

    private fun logFailureIfNeeded(
        event: GateModeEvent,
        output: LiveFramePipelineOutput,
    ) {
        val reason = when (event) {
            is GateModeEvent.LivenessFailed -> event.reason
            is GateModeEvent.MatchNotFound -> event.reason
            GateModeEvent.Timeout -> "Timed out waiting for attendance gates"
            else -> return
        }
        viewModelScope.launch {
            failedRecognitionRepository.saveFailure(
                FailedRecognitionDraft(
                    schoolId = attendanceRules.schoolId,
                    deviceId = attendanceRules.deviceId,
                    reason = reason,
                    qualityScore = output.faceDetectionResult?.quality?.score ?: 0f,
                    livenessScore = output.livenessResult?.score ?: 0f,
                ),
            )
        }
    }

    private fun isDuplicate(studentId: String): Boolean {
        val lastMarkedAt = recentMarks[studentId] ?: return false
        return System.currentTimeMillis() - lastMarkedAt < attendanceRules.duplicateScanCooldownMinutes * 60_000L
    }

    private fun publishState(
        state: GateModeState,
        liveMessage: String? = null,
    ) {
        val currentPendingCount = _uiState.value.pendingSyncCount
        _uiState.value = GateAttendanceUiState(
            gateState = state,
            message = liveMessage?.takeUnless { state.isTerminal() } ?: state.message(),
            student = state.studentOrNull(),
            pendingSyncCount = currentPendingCount,
            lastStateChangedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun scheduleReset() {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(AutoResetMillis)
            stableDecisionStartedAtMillis = null
            publishState(stateMachine.reset())
        }
    }

    private fun GateModeState.isTerminal(): Boolean =
        this is GateModeState.AttendanceMarked ||
            this is GateModeState.DuplicateScan ||
            this is GateModeState.ManualReviewRequired

    private fun GateModeState.studentOrNull(): StudentAttendanceResult? =
        when (this) {
            is GateModeState.AttendanceMarked -> student
            is GateModeState.DuplicateScan -> student
            else -> null
        }

    private fun GateModeState.message(): String =
        when (this) {
            GateModeState.IdleWaitingForFace -> "Waiting for student..."
            GateModeState.FaceDetected -> "Face detected"
            GateModeState.FaceQualityChecking -> "Checking face quality..."
            GateModeState.HoldStill -> "Hold still..."
            GateModeState.CheckingLiveness -> "Checking liveness..."
            GateModeState.GeneratingEmbeddingPlaceholder -> "Preparing recognition..."
            GateModeState.MatchingStudentPlaceholder -> "Matching student..."
            is GateModeState.AttendanceMarked -> "Attendance marked"
            is GateModeState.DuplicateScan -> "Duplicate scan ignored"
            is GateModeState.ManualReviewRequired -> reason
        }

    companion object {
        private const val AutoResetMillis = 1_500L
        fun factory(
            attendanceEventRepository: AttendanceEventRepository,
            studentRepository: StudentRepository,
            failedRecognitionRepository: FailedRecognitionRepository,
            settingsRepository: SettingsRepository,
            recognitionCalibrationLogRepository: RecognitionCalibrationLogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                GateAttendanceViewModel(
                    attendanceEventRepository = attendanceEventRepository,
                    studentRepository = studentRepository,
                    failedRecognitionRepository = failedRecognitionRepository,
                    settingsRepository = settingsRepository,
                    recognitionCalibrationLogRepository = recognitionCalibrationLogRepository,
                ) as T
        }
    }
}
