package com.schoollog.attendance.enrollment.data

import com.schoollog.attendance.enrollment.domain.EnrollmentProfile

interface EnrollmentStore {
    suspend fun save(profile: EnrollmentProfile)
}
