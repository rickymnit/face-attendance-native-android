package com.schoollog.attendance.enrollment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoollog.attendance.camera.domain.LiveFramePipelineOutput
import com.schoollog.attendance.core.common.AttendanceRules
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.enrollment.domain.EnrollmentForm
import com.schoollog.attendance.enrollment.domain.EnrollmentRepository
import com.schoollog.attendance.enrollment.domain.EnrollmentResult
import com.schoollog.attendance.enrollment.domain.LiveEnrollmentResult
import com.schoollog.attendance.ml.recognition.EmbeddingFailureReason
import com.schoollog.attendance.ml.recognition.EmbeddingResult
import com.schoollog.attendance.ml.recognition.ModelMetadata
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EnrollmentViewModel(
    private val enrollmentRepository: EnrollmentRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    private var attendanceRules = AttendanceRules()
    private val capturedEmbeddings = mutableListOf<EmbeddingResult>()
    private val capturedQualityScores = mutableListOf<Float>()
    private val capturedFrameTimestamps = mutableSetOf<Long>()
    private var saveLiveEnrollmentJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.attendanceRules.collect { rules -> attendanceRules = rules }
        }
        viewModelScope.launch {
            settingsRepository.attendanceRules
                .flatMapLatest { rules -> enrollmentRepository.observeStudents(rules.schoolId) }
                .collect { students ->
                    _uiState.update { it.copy(students = students) }
                }
        }
    }

    fun onStudentIdChanged(value: String) = updateForm { copy(studentId = value) }
    fun onNameChanged(value: String) = updateForm { copy(name = value) }
    fun onClassChanged(value: String) = updateForm { copy(className = value) }
    fun onSectionChanged(value: String) = updateForm { copy(section = value) }
    fun onRollNumberChanged(value: String) = updateForm { copy(rollNumber = value) }

    fun toggleReEnrollment() {
        _uiState.update {
            it.copy(
                allowReEnrollment = !it.allowReEnrollment,
                statusMessage = null,
                isSuccess = false,
            )
        }
    }

    fun searchExistingStudent() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Student ID is required", isSuccess = false)
            }
            return
        }

        viewModelScope.launch {
            val existing = enrollmentRepository.searchStudent(attendanceRules.schoolId, studentId)
            _uiState.update { current ->
                if (existing == null) {
                    current.copy(
                        statusMessage = "No existing student found. You can create a new enrollment.",
                        isSuccess = false,
                    )
                } else {
                    current.copy(
                        studentId = existing.studentId,
                        name = existing.displayName,
                        className = existing.className,
                        section = existing.section,
                        rollNumber = existing.rollNumber,
                        statusMessage = "Existing student loaded",
                        isSuccess = true,
                    )
                }
            }
        }
    }

    fun startFaceEnrollment() {
        val form = _uiState.value.toEnrollmentForm().trimmed()
        validate(form)?.let { message ->
            _uiState.update { it.copy(statusMessage = message, isSuccess = false) }
            return
        }

        viewModelScope.launch {
            val activeEmbeddingExists = enrollmentRepository.hasActiveEmbedding(
                schoolId = attendanceRules.schoolId,
                studentId = form.studentId,
                modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
            )
            if (activeEmbeddingExists && !_uiState.value.allowReEnrollment) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Active face enrollment already exists. Enable Re-enroll to replace it.",
                        isSuccess = false,
                    )
                }
                return@launch
            }

            resetLiveSamples()
            _uiState.update {
                it.copy(
                    isEnrollmentCameraActive = true,
                    liveEnrollmentMessage = "Starting live enrollment camera",
                    statusMessage = null,
                    isSuccess = false,
                    capturedSampleCount = 0,
                    sampleStatuses = listOf("Waiting", "Waiting", "Waiting"),
                )
            }
        }
    }

    fun stopFaceEnrollment() {
        _uiState.update {
            it.copy(
                isEnrollmentCameraActive = false,
                liveEnrollmentMessage = "Live enrollment stopped",
            )
        }
        resetLiveSamples()
    }

    fun onLiveEnrollmentOutput(output: LiveFramePipelineOutput) {
        if (!_uiState.value.isEnrollmentCameraActive || saveLiveEnrollmentJob?.isActive == true) return

        _uiState.update { it.copy(liveEnrollmentMessage = output.userMessage) }
        val embedding = output.embeddingResult ?: return
        if (!embedding.isSuccess) {
            val message = embedding.failureReason.toUiMessage()
            _uiState.update {
                it.copy(
                    liveEnrollmentMessage = message,
                    statusMessage = message,
                    isEnrollmentCameraActive = embedding.failureReason != EmbeddingFailureReason.MODEL_NOT_FOUND,
                    isSuccess = false,
                )
            }
            if (embedding.failureReason == EmbeddingFailureReason.MODEL_NOT_FOUND) resetLiveSamples()
            return
        }

        if (!capturedFrameTimestamps.add(embedding.sourceFrameTimestampNanos)) return
        capturedEmbeddings += embedding
        capturedQualityScores += output.faceDetectionResult?.quality?.score ?: 0f
        val sampleNumber = capturedEmbeddings.size.coerceIn(1, RequiredSampleCount)
        _uiState.update { current ->
            current.copy(
                capturedSampleCount = capturedEmbeddings.size.coerceAtMost(RequiredSampleCount),
                sampleStatuses = current.sampleStatuses.markCaptured(sampleNumber),
                liveEnrollmentMessage = "Sample $sampleNumber captured",
            )
        }

        if (capturedEmbeddings.size >= RequiredSampleCount) {
            saveLiveEnrollment()
        }
    }

    fun saveEnrollment() {
        val form = _uiState.value.toEnrollmentForm()
        viewModelScope.launch {
            when (val result = enrollmentRepository.saveEnrollment(attendanceRules.schoolId, form)) {
                is EnrollmentResult.Saved -> {
                    val message = if (result.debugPlaceholderEmbeddingCreated) {
                        "Enrollment saved with debug placeholder embedding"
                    } else {
                        "Enrollment saved. Start live face enrollment to add the real embedding."
                    }
                    _uiState.update {
                        EnrollmentUiState(
                            statusMessage = message,
                            isSuccess = true,
                            students = it.students,
                        )
                    }
                }
                is EnrollmentResult.DuplicateStudent -> {
                    _uiState.update {
                        it.copy(
                            statusMessage = "Student ${result.studentId} is already enrolled",
                            isSuccess = false,
                        )
                    }
                }
                is EnrollmentResult.ValidationError -> {
                    _uiState.update {
                        it.copy(statusMessage = result.message, isSuccess = false)
                    }
                }
            }
        }
    }

    private fun saveLiveEnrollment() {
        val form = _uiState.value.toEnrollmentForm().trimmed()
        val finalEmbedding = averageAndNormalize(capturedEmbeddings.map { it.embedding }) ?: run {
            _uiState.update {
                it.copy(
                    isEnrollmentCameraActive = false,
                    statusMessage = "Could not create final face embedding",
                    liveEnrollmentMessage = "Enrollment failed",
                    isSuccess = false,
                )
            }
            resetLiveSamples()
            return
        }
        val modelVersion = capturedEmbeddings.first().modelVersion
        val qualityScore = capturedQualityScores.average().takeIf { it.isFinite() }?.toFloat() ?: 0f

        saveLiveEnrollmentJob = viewModelScope.launch {
            _uiState.update { it.copy(isSavingLiveEnrollment = true, liveEnrollmentMessage = "Saving enrollment...") }
            when (val result = enrollmentRepository.saveLiveEnrollment(
                schoolId = attendanceRules.schoolId,
                form = form,
                embedding = finalEmbedding,
                modelVersion = modelVersion,
                qualityScore = qualityScore,
                allowReEnrollment = _uiState.value.allowReEnrollment,
            )) {
                LiveEnrollmentResult.Saved -> {
                    _uiState.update {
                        it.copy(
                            isEnrollmentCameraActive = false,
                            isSavingLiveEnrollment = false,
                            liveEnrollmentMessage = "Enrollment successful",
                            statusMessage = "Enrollment successful",
                            isSuccess = true,
                            capturedSampleCount = RequiredSampleCount,
                        )
                    }
                    resetLiveSamples()
                }
                is LiveEnrollmentResult.ActiveEmbeddingExists -> {
                    _uiState.update {
                        it.copy(
                            isEnrollmentCameraActive = false,
                            isSavingLiveEnrollment = false,
                            liveEnrollmentMessage = "Re-enroll required",
                            statusMessage = "Active face enrollment already exists for ${result.studentId}",
                            isSuccess = false,
                        )
                    }
                    resetLiveSamples()
                }
                is LiveEnrollmentResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isEnrollmentCameraActive = false,
                            isSavingLiveEnrollment = false,
                            liveEnrollmentMessage = "Enrollment failed",
                            statusMessage = result.message,
                            isSuccess = false,
                        )
                    }
                    resetLiveSamples()
                }
            }
        }
    }

    private fun updateForm(block: EnrollmentUiState.() -> EnrollmentUiState) {
        _uiState.update { it.block().copy(statusMessage = null, isSuccess = false) }
    }

    private fun EnrollmentUiState.toEnrollmentForm(): EnrollmentForm =
        EnrollmentForm(
            studentId = studentId,
            name = name,
            className = className,
            section = section,
            rollNumber = rollNumber,
        )

    private fun EnrollmentForm.trimmed(): EnrollmentForm =
        copy(
            studentId = studentId.trim(),
            name = name.trim(),
            className = className.trim(),
            section = section.trim(),
            rollNumber = rollNumber.trim(),
        )

    private fun validate(form: EnrollmentForm): String? =
        when {
            form.studentId.isBlank() -> "Student ID is required"
            form.name.isBlank() -> "Name is required"
            form.className.isBlank() -> "Class is required"
            else -> null
        }

    private fun List<String>.markCaptured(sampleNumber: Int): List<String> =
        mapIndexed { index, value ->
            if (index == sampleNumber - 1) "Sample $sampleNumber captured" else value
        }

    private fun averageAndNormalize(embeddings: List<FloatArray>): FloatArray? {
        val size = embeddings.firstOrNull()?.size ?: return null
        if (size <= 0 || embeddings.any { it.size != size }) return null
        val averaged = FloatArray(size)
        embeddings.forEach { embedding ->
            for (index in 0 until size) averaged[index] += embedding[index]
        }
        for (index in 0 until size) averaged[index] /= embeddings.size
        val norm = sqrt(averaged.fold(0f) { sum, value -> sum + value * value })
        if (!norm.isFinite() || norm <= 0f) return null
        return FloatArray(size) { index -> averaged[index] / norm }
    }

    private fun EmbeddingFailureReason.toUiMessage(): String =
        when (this) {
            EmbeddingFailureReason.MODEL_NOT_FOUND -> "Face model not installed"
            EmbeddingFailureReason.MODEL_LOAD_FAILED -> "Face model failed to load"
            EmbeddingFailureReason.INPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.INPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_SIZE_MISMATCH -> "Face model does not match app configuration"
            EmbeddingFailureReason.INVALID_INPUT -> "Invalid live face sample"
            EmbeddingFailureReason.INFERENCE_FAILED -> "Face model inference failed"
            EmbeddingFailureReason.INVALID_OUTPUT -> "Face model output invalid"
            EmbeddingFailureReason.SUCCESS -> "Face embedding ready"
        }

    private fun resetLiveSamples() {
        capturedEmbeddings.clear()
        capturedQualityScores.clear()
        capturedFrameTimestamps.clear()
    }

    companion object {
        private const val RequiredSampleCount = 3

        fun factory(
            enrollmentRepository: EnrollmentRepository,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EnrollmentViewModel(enrollmentRepository, settingsRepository) as T
            }
    }
}
