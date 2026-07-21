package com.schoollog.attendance.camera.domain

data class CameraFrameInfo(
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
) {
    val analysisWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    val analysisHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
}
