package com.schoollog.attendance.ml.liveness

import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.ml.face.FaceDetectionResult

data class LivenessFrameSample(
    val frameInfo: CameraFrameInfo,
    val faceDetectionResult: FaceDetectionResult,
)
