package com.schoollog.attendance.debugqa.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.debugqa.data.TfliteModelSmokeTester
import com.schoollog.attendance.debugqa.domain.CalibrationFaceCondition
import com.schoollog.attendance.debugqa.domain.CalibrationLightingCondition
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogRepository
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationSession
import com.schoollog.attendance.debugqa.domain.RecognitionQaRepository
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.ml.recognition.RecognitionThresholdConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecognitionQaViewModel(
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val settingsRepository: SettingsRepository,
    private val recognitionQaRepository: RecognitionQaRepository,
    private val recognitionCalibrationLogRepository: RecognitionCalibrationLogRepository,
    private val modelSmokeTester: TfliteModelSmokeTester,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecognitionQaUiState())
    val uiState: StateFlow<RecognitionQaUiState> = _uiState.asStateFlow()

    private var attendanceRules = AttendanceRules()

    init {
        viewModelScope.launch {
            settingsRepository.attendanceRules.collect { rules ->
                attendanceRules = rules
                val thresholds = RecognitionThresholdConfig.forMode(rules.recognitionMode)
                _uiState.update {
                    it.copy(
                        schoolId = rules.schoolId,
                        recognitionMode = rules.recognitionMode,
                        thresholdAcceptance = thresholds.acceptanceThreshold,
                        thresholdMargin = thresholds.ambiguityGap,
                        modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
                        inputWidth = ModelMetadata.DefaultFaceEmbedding.inputWidth,
                        inputHeight = ModelMetadata.DefaultFaceEmbedding.inputHeight,
                        inputChannels = ModelMetadata.DefaultFaceEmbedding.inputChannels,
                        inputDataType = ModelMetadata.DefaultFaceEmbedding.inputDataType.name,
                        embeddingSize = ModelMetadata.DefaultFaceEmbedding.embeddingSize,
                    )
                }
                refreshEmbeddingSummary()
            }
        }

        viewModelScope.launch {
            RecognitionCalibrationSession.state.collect { session ->
                _uiState.update {
                    it.copy(
                        sessionId = session.sessionId,
                        testerName = session.testerName,
                        sessionNotes = session.notes,
                        expectedStudentId = session.expectedStudentId,
                        lightingCondition = session.lightingCondition,
                        faceCondition = session.faceCondition,
                    )
                }
                refreshCalibrationSummary()
            }
        }
        viewModelScope.launch {
            recognitionCalibrationLogRepository.latestLog.collect { log ->
                _uiState.update { current ->
                    current.copy(
                        lastTop1StudentId = log?.top1StudentId,
                        lastTop1Score = log?.top1Score,
                        lastTop2StudentId = log?.top2StudentId,
                        lastTop2Score = log?.top2Score,
                        lastTop3StudentId = log?.top3StudentId,
                        lastTop3Score = log?.top3Score,
                        lastTop1Top2Margin = log?.margin,
                        lastDecision = log?.decision ?: "--",
                        lastCalibrationFailureReason = log?.failureReason ?: "None",
                        lastTotalDecisionTimeMs = log?.totalDecisionTimeMs ?: 0.0,
                    )
                }
                refreshCalibrationSummary()
            }
        }
        viewModelScope.launch {
            PerformanceMonitor.metrics.collect { metrics ->
                _uiState.update {
                    it.copy(
                        averageMatchingMillis = metrics.averageMatchingMillis,
                        lastTopMatchScores = metrics.lastTopMatchScores,
                        lastFailureReason = metrics.lastFailureReason,
                    )
                }
            }
        }
    }

    fun reloadEmbeddingCache() {
        if (!BuildConfig.DEBUG) return
        val version = recognitionQaRepository.reloadEmbeddingCacheDebugOnly()
        _uiState.update { it.copy(cacheVersion = version, statusMessage = "Embedding cache reload requested") }
        refreshEmbeddingSummary()
    }

    fun clearLocalAttendanceEvents() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            recognitionQaRepository.clearLocalAttendanceEventsDebugOnly()
            _uiState.update { it.copy(statusMessage = "Local attendance events cleared") }
        }
    }

    fun clearLocalEmbeddings() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            recognitionQaRepository.clearLocalEmbeddingsForSchoolDebugOnly(attendanceRules.schoolId)
            _uiState.update {
                it.copy(
                    enrolledStudentCount = 0,
                    cacheVersion = it.cacheVersion + 1,
                    statusMessage = "Local embeddings cleared for ${attendanceRules.schoolId}",
                )
            }
        }
    }

    fun runModelSmokeTest() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningModelSmokeTest = true,
                    statusMessage = "Running model smoke test...",
                    lastModelError = null,
                )
            }
            val result = modelSmokeTester.run()
            _uiState.update {
                it.copy(
                    modelFound = result.modelFound,
                    modelLoaded = result.modelLoaded,
                    modelSmokeTestPassed = result.success,
                    modelVersion = result.metadata.modelVersion,
                    inputWidth = result.metadata.inputWidth,
                    inputHeight = result.metadata.inputHeight,
                    inputChannels = result.metadata.inputChannels,
                    inputDataType = result.inputDataType ?: result.metadata.inputDataType.name,
                    actualInputShape = result.inputShape ?: "--",
                    actualOutputShape = result.outputShape ?: "--",
                    actualOutputDataType = result.outputDataType ?: "--",
                    embeddingSize = result.metadata.embeddingSize,
                    lastModelInferenceMillis = result.averageInferenceTimeMillis,
                    lastModelError = result.error,
                    isRunningModelSmokeTest = false,
                    statusMessage = if (result.success) "Model smoke test passed" else "Model smoke test failed",
                )
            }
        }
    }

    fun refreshEmbeddingSummary() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val embeddings = faceEmbeddingRepository.getActiveEmbeddingsForSchool(
                schoolId = attendanceRules.schoolId,
                modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
            )
            val embeddingSize = embeddings.firstOrNull()?.embeddingSize ?: ModelMetadata.DefaultFaceEmbedding.embeddingSize
            _uiState.update {
                it.copy(
                    enrolledStudentCount = embeddings.map { embedding -> embedding.erpStudentId }.distinct().size,
                    modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
                    embeddingSize = embeddingSize,
                    isLoading = false,
                )
            }
        }
    }

    fun startNewCalibrationSession() {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.startNewSession()
        _uiState.update { it.copy(statusMessage = "New calibration session started") }
    }

    fun onTesterNameChanged(value: String) {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.setTesterName(value)
    }

    fun onSessionNotesChanged(value: String) {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.setNotes(value)
    }

    fun onLightingConditionChanged(value: CalibrationLightingCondition) {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.setLightingCondition(value)
    }

    fun onFaceConditionChanged(value: CalibrationFaceCondition) {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.setFaceCondition(value)
    }

    fun refreshCalibrationSummary() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val summary = recognitionCalibrationLogRepository.summary()
            _uiState.update {
                it.copy(
                    genuineAccepted = summary.genuineAccepted,
                    genuineRejected = summary.genuineRejected,
                    wrongAccepted = summary.wrongAccepted,
                    ambiguousRejected = summary.ambiguousRejected,
                    lowConfidenceRejected = summary.lowConfidenceRejected,
                    averageDecisionTimeMs = summary.averageDecisionTimeMs,
                )
            }
        }
    }

    fun onExpectedStudentIdChanged(value: String) {
        if (!BuildConfig.DEBUG) return
        RecognitionCalibrationSession.setExpectedStudentId(value)
    }

    fun exportRecognitionCalibrationCsv(onCsvReady: (String) -> Unit) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val csv = recognitionCalibrationLogRepository.exportCsv()
            _uiState.update { it.copy(statusMessage = "Recognition calibration CSV ready") }
            onCsvReady(csv)
        }
    }

    fun anonymizedPerformanceLog(): String {
        val state = _uiState.value
        return buildString {
            appendLine("Schoollog Recognition QA")
            appendLine("debugBuild=${BuildConfig.DEBUG}")
            appendLine("schoolIdHash=${state.schoolId.hashCode()}")
            appendLine("modelVersion=${state.modelVersion}")
            appendLine("configuredInput=${state.inputWidth}x${state.inputHeight}x${state.inputChannels} ${state.inputDataType}")
            appendLine("actualInputShape=${state.actualInputShape}")
            appendLine("actualOutputShape=${state.actualOutputShape} ${state.actualOutputDataType}")
            appendLine("embeddingSize=${state.embeddingSize}")
            appendLine("recognitionMode=${state.recognitionMode.name}")
            appendLine("calibrationSessionId=${state.sessionId}")
            appendLine("lightingCondition=${state.lightingCondition.name}")
            appendLine("faceCondition=${state.faceCondition.name}")
            appendLine("thresholdAcceptance=${state.thresholdAcceptance.formatScore()}")
            appendLine("thresholdMargin=${state.thresholdMargin.formatScore()}")
            appendLine("enrolledStudentCount=${state.enrolledStudentCount}")
            appendLine("averageMatchingMillis=${state.averageMatchingMillis.formatMetric()}")
            appendLine("averageDecisionTimeMs=${state.averageDecisionTimeMs.formatMetric()}")
            appendLine("wrongAccepted=${state.wrongAccepted}")
            appendLine("lastTop3Scores=${state.lastTopMatchScores.joinToString(prefix = "[", postfix = "]") { it.formatScore() }}")
            appendLine("lastFailureReason=${state.lastFailureReason}")
            appendLine("modelFound=${state.modelFound ?: "not_tested"}")
            appendLine("modelLoaded=${state.modelLoaded ?: "not_tested"}")
            appendLine("modelSmokeTestPassed=${state.modelSmokeTestPassed ?: "not_tested"}")
            appendLine("lastModelInferenceMillis=${state.lastModelInferenceMillis?.formatMetric() ?: "--"}")
            appendLine("lastModelError=${state.lastModelError ?: "None"}")
            appendLine("cacheVersion=${state.cacheVersion}")
            appendLine("containsFaceImages=false")
            appendLine("containsRawEmbeddings=false")
        }
    }

    private fun Double.formatMetric(): String = String.format("%.2f", this)
    private fun Float.formatScore(): String = String.format("%.3f", this)

    companion object {
        fun factory(
            faceEmbeddingRepository: FaceEmbeddingRepository,
            settingsRepository: SettingsRepository,
            recognitionQaRepository: RecognitionQaRepository,
            recognitionCalibrationLogRepository: RecognitionCalibrationLogRepository,
            modelSmokeTester: TfliteModelSmokeTester,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RecognitionQaViewModel(
                    faceEmbeddingRepository = faceEmbeddingRepository,
                    settingsRepository = settingsRepository,
                    recognitionQaRepository = recognitionQaRepository,
                    recognitionCalibrationLogRepository = recognitionCalibrationLogRepository,
                    modelSmokeTester = modelSmokeTester,
                ) as T
        }
    }
}
