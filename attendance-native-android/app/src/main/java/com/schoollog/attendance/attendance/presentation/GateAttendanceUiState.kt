package com.schoollog.attendance.attendance.presentation

import com.schoollog.attendance.attendance.domain.GateModeState
import com.schoollog.attendance.attendance.domain.StudentAttendanceResult

data class GateAttendanceUiState(
    val gateState: GateModeState = GateModeState.IdleWaitingForFace,
    val message: String = "Waiting for student...",
    val student: StudentAttendanceResult? = null,
    val pendingSyncCount: Int = 0,
    val lastStateChangedAtMillis: Long = System.currentTimeMillis(),
)
