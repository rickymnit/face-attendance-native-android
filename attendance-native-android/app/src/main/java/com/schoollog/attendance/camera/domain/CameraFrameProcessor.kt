package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult

interface CameraFrameProcessor {
    fun process(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
        faceCropResult: FaceCropResult? = null,
    ): LiveFramePipelineOutput
}
