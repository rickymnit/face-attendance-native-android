package com.schoollog.attendance.ml.face

enum class FaceQualityFailureReason(val displayText: String) {
    NO_FACE("No face detected"),
    MULTIPLE_FACES("Multiple faces detected"),
    FACE_TOO_SMALL("Face too small"),
    FACE_NOT_CENTERED("Face not centered"),
    FACE_TOO_TILTED("Face too tilted"),
    FACE_PARTIALLY_OUTSIDE_FRAME("Face partially outside frame"),
    LOW_LIGHT_PLACEHOLDER("Low light placeholder"),
    BLUR_PLACEHOLDER("Blur placeholder"),
    PASSED("Face detected"),
}
