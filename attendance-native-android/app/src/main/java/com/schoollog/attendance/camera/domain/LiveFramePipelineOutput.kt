package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.liveness.LivenessResult
import com.schoollog.attendance.ml.recognition.EmbeddingResult
import com.schoollog.attendance.ml.recognition.FaceMatchResult
import com.schoollog.attendance.ml.recognition.RecognitionDecision

data class LiveFramePipelineOutput(
    val state: LiveFramePipelineState,
    val userMessage: String,
    val processedAtMillis: Long,
    val processedFrameCount: Long,
    val recognitionDecision: RecognitionDecision,
    val stableFaceTrackingResult: StableFaceTrackingResult? = null,
    val faceDetectionResult: FaceDetectionResult? = null,
    val faceCropResult: FaceCropResult? = null,
    val livenessResult: LivenessResult? = null,
    val embeddingResult: EmbeddingResult? = null,
    val embeddingInferenceTimeMillis: Double? = null,
    val faceMatchResult: FaceMatchResult? = null,
)
