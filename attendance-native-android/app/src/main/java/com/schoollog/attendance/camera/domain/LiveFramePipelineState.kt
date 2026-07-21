package com.schoollog.attendance.camera.domain

enum class LiveFramePipelineState {
    WAITING_FOR_FACE,
    FACE_DETECTED,
    HOLD_STILL,
    FACE_STABLE,
    READY_FOR_LIVENESS,
    CHECKING_LIVENESS,
    READY_FOR_RECOGNITION_PLACEHOLDER,
    ERROR,
}
