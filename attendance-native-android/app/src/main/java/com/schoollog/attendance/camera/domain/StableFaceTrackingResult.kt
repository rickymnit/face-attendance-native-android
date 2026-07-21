package com.schoollog.attendance.camera.domain

data class StableFaceTrackingResult(
    val state: StableFaceTrackerState,
    val stableDurationMillis: Long,
) {
    val isReadyForLiveness: Boolean = state == StableFaceTrackerState.READY_FOR_LIVENESS
}
