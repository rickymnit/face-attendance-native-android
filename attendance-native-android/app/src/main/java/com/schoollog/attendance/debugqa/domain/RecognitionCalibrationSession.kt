package com.schoollog.attendance.debugqa.domain

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecognitionCalibrationSessionState(
    val sessionId: String = UUID.randomUUID().toString(),
    val testerName: String = "",
    val notes: String = "",
    val expectedStudentId: String = "",
    val lightingCondition: CalibrationLightingCondition = CalibrationLightingCondition.NORMAL,
    val faceCondition: CalibrationFaceCondition = CalibrationFaceCondition.NORMAL,
)

object RecognitionCalibrationSession {
    private val _state = MutableStateFlow(RecognitionCalibrationSessionState())
    val state: StateFlow<RecognitionCalibrationSessionState> = _state.asStateFlow()

    fun startNewSession() {
        _state.value = _state.value.copy(sessionId = UUID.randomUUID().toString(), expectedStudentId = "")
    }

    fun setTesterName(value: String) {
        _state.value = _state.value.copy(testerName = value)
    }

    fun setNotes(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun setExpectedStudentId(value: String) {
        _state.value = _state.value.copy(expectedStudentId = value.trim())
    }

    fun setLightingCondition(value: CalibrationLightingCondition) {
        _state.value = _state.value.copy(lightingCondition = value)
    }

    fun setFaceCondition(value: CalibrationFaceCondition) {
        _state.value = _state.value.copy(faceCondition = value)
    }

    fun currentState(): RecognitionCalibrationSessionState = _state.value

    fun currentExpectedStudentId(): String? =
        _state.value.expectedStudentId.trim().takeIf { it.isNotEmpty() }
}
