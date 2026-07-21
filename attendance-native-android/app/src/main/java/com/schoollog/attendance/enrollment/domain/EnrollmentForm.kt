package com.schoollog.attendance.enrollment.domain

data class EnrollmentForm(
    val studentId: String,
    val name: String,
    val className: String,
    val section: String,
    val rollNumber: String,
)
