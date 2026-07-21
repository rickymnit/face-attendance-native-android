package com.schoollog.attendance.core.navigation

sealed class AppDestination(val route: String) {
    data object Setup : AppDestination("setup")
    data object Home : AppDestination("home")
    data object GateCamera : AppDestination("gate_camera")
    data object Enrollment : AppDestination("enrollment")
    data object Settings : AppDestination("settings")
    data object RecognitionQa : AppDestination("recognition_qa")
    data object PilotReadiness : AppDestination("pilot_readiness")
    data object AndroidStressTest : AppDestination("android_stress_test")
}
