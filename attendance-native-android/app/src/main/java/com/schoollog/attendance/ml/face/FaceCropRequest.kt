package com.schoollog.attendance.ml.face

import androidx.camera.core.ImageProxy

data class FaceCropRequest(
    val imageProxy: ImageProxy,
    val rotationDegrees: Int,
    val boundingBox: FaceBoundingBox,
    val frameWidth: Int,
    val frameHeight: Int,
    val isFrontCameraMirrored: Boolean,
)
