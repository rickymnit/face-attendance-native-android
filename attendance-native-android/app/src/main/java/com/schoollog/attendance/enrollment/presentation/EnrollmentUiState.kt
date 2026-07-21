package com.schoollog.attendance.enrollment.presentation

import com.schoollog.attendance.enrollment.domain.EnrollmentProfile

data class EnrollmentUiState(
    val studentId: String = "",
    val name: String = "",
    val className: String = "",
    val section: String = "",
    val rollNumber: String = "",
    val statusMessage: String? = null,
    val liveEnrollmentMessage: String = "Live enrollment not started",
    val isSuccess: Boolean = false,
    val isEnrollmentCameraActive: Boolean = false,
    val isSavingLiveEnrollment: Boolean = false,
    val allowReEnrollment: Boolean = false,
    val capturedSampleCount: Int = 0,
    val sampleStatuses: List<String> = listOf("Waiting", "Waiting", "Waiting"),
    val students: List<EnrollmentProfile> = emptyList(),
)
