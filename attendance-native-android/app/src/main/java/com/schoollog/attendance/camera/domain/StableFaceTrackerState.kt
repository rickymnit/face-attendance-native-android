package com.schoollog.attendance.camera.domain

enum class StableFaceTrackerState {
    WAITING_FOR_FACE,
    FACE_DETECTED,
    HOLD_STILL,
    FACE_STABLE,
    READY_FOR_LIVENESS,
}
