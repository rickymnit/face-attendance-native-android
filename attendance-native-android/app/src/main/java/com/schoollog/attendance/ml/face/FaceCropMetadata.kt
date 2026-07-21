package com.schoollog.attendance.ml.face

data class FaceCropMetadata(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotatedWidth: Int,
    val rotatedHeight: Int,
    val rotationDegrees: Int,
    val mirrored: Boolean,
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val normalizedSize: Int,
)
