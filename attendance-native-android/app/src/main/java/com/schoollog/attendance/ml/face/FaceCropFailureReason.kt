package com.schoollog.attendance.ml.face

enum class FaceCropFailureReason {
    CROP_OUT_OF_BOUNDS,
    FACE_TOO_SMALL,
    ROTATION_ERROR,
    FRAME_CONVERSION_ERROR,
    SUCCESS,
}
