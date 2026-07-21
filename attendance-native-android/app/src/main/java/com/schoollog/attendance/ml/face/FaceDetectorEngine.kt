package com.schoollog.attendance.ml.face

import androidx.camera.core.ImageProxy
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.google.android.gms.tasks.Task

interface FaceDetectorEngine : AutoCloseable {
    fun detect(
        imageProxy: ImageProxy,
        frameInfo: CameraFrameInfo,
    ): Task<FaceDetectionResult>

    override fun close() = Unit
}
