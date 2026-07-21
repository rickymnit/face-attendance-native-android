package com.schoollog.attendance.enrollment.domain

data class EnrollmentProfile(
    val studentId: String,
    val displayName: String,
    val className: String,
    val section: String,
    val rollNumber: String,
)
