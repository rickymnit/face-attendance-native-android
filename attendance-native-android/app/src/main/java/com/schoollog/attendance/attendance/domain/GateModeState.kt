package com.schoollog.attendance.attendance.domain

sealed interface GateModeState {
    data object IdleWaitingForFace : GateModeState
    data object FaceDetected : GateModeState
    data object FaceQualityChecking : GateModeState
    data object HoldStill : GateModeState
    data object CheckingLiveness : GateModeState
    data object GeneratingEmbeddingPlaceholder : GateModeState
    data object MatchingStudentPlaceholder : GateModeState
    data class AttendanceMarked(val student: StudentAttendanceResult) : GateModeState
    data class DuplicateScan(val student: StudentAttendanceResult) : GateModeState
    data class ManualReviewRequired(val reason: String) : GateModeState
}
