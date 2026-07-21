package com.schoollog.attendance.attendance.domain

sealed interface GateModeEvent {
    data object FrameReceived : GateModeEvent
    data object FaceDetected : GateModeEvent
    data object FaceQualityPassed : GateModeEvent
    data class FaceQualityFailed(val reason: String) : GateModeEvent
    data object FaceStable : GateModeEvent
    data object LivenessPassed : GateModeEvent
    data class LivenessFailed(val reason: String) : GateModeEvent
    data object EmbeddingGeneratedPlaceholder : GateModeEvent
    data class MatchFound(val student: StudentAttendanceResult) : GateModeEvent
    data class MatchNotFound(val reason: String) : GateModeEvent
    data class DuplicateFound(val student: StudentAttendanceResult) : GateModeEvent
    data class AttendanceSaved(val student: StudentAttendanceResult) : GateModeEvent
    data object Timeout : GateModeEvent
    data object ResetForNextStudent : GateModeEvent
}
