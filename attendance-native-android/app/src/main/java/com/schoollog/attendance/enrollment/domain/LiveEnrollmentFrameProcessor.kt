package com.schoollog.attendance.enrollment.domain

import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.CameraFrameProcessor
import com.schoollog.attendance.camera.domain.LiveFramePipelineOutput
import com.schoollog.attendance.camera.domain.LiveFramePipelineState
import com.schoollog.attendance.camera.domain.StableFaceTracker
import com.schoollog.attendance.camera.domain.StableFaceTrackerState
import com.schoollog.attendance.camera.domain.StableFaceTrackingResult
import com.schoollog.attendance.ml.face.FaceCropFailureReason
import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.face.FaceQualityFailureReason
import com.schoollog.attendance.ml.liveness.LivenessDecision
import com.schoollog.attendance.ml.liveness.LivenessEngine
import com.schoollog.attendance.ml.liveness.LivenessEngineV0
import com.schoollog.attendance.ml.liveness.LivenessFrameSample
import com.schoollog.attendance.ml.recognition.EmbeddingFailureReason
import com.schoollog.attendance.ml.recognition.FaceEmbeddingEngine
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import java.util.ArrayDeque
import kotlin.math.abs

class LiveEnrollmentFrameProcessor(
    private val faceEmbeddingEngine: FaceEmbeddingEngine,
    private val livenessEngine: LivenessEngine = LivenessEngineV0(),
    private val stableFaceTracker: StableFaceTracker = StableFaceTracker(),
) : CameraFrameProcessor {
    private val recentLivenessSamples = ArrayDeque<LivenessFrameSample>(MaxRecentLivenessSamples)
    private var processedFrameCount = 0L
    private var capturedSampleCount = 0
    private var lastSampleCapturedAtNanos = 0L

    override fun process(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
        faceCropResult: FaceCropResult?,
    ): LiveFramePipelineOutput {
        processedFrameCount += 1

        if (capturedSampleCount >= RequiredSamples) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = "Enrollment samples captured",
                recognitionDecision = RecognitionDecision.MATCH_ACCEPTED,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
            )
        }

        if (!faceDetectionResult.hasSelectedPrimaryFace) {
            recentLivenessSamples.clear()
            val tracking = stableFaceTracker.update(frameInfo, faceDetectionResult)
            return output(
                state = LiveFramePipelineState.WAITING_FOR_FACE,
                userMessage = faceDetectionResult.quality.guardMessage(),
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        if (!faceDetectionResult.quality.passes) {
            recentLivenessSamples.clear()
            val tracking = stableFaceTracker.update(frameInfo, faceDetectionResult)
            return output(
                state = LiveFramePipelineState.FACE_DETECTED,
                userMessage = faceDetectionResult.quality.guardMessage(),
                recognitionDecision = RecognitionDecision.FACE_QUALITY_FAILED,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        val tracking = stableFaceTracker.update(frameInfo, faceDetectionResult)
        if (!tracking.isReadyForLiveness) {
            recentLivenessSamples.clear()
            return output(
                state = tracking.state.toPipelineState(),
                userMessage = tracking.guardMessage(),
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        rememberLivenessSample(frameInfo, faceDetectionResult)
        val liveness = livenessEngine.evaluate(recentLivenessSamples.toList())
        if (liveness.decision != LivenessDecision.PASS) {
            return output(
                state = LiveFramePipelineState.CHECKING_LIVENESS,
                userMessage = if (liveness.decision == LivenessDecision.FAIL) {
                    "Liveness failed. Please try again"
                } else {
                    "Checking liveness..."
                },
                recognitionDecision = if (liveness.decision == LivenessDecision.FAIL) {
                    RecognitionDecision.LIVENESS_FAILED
                } else {
                    RecognitionDecision.UNCERTAIN
                },
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                livenessResult = liveness,
            )
        }

        if (faceCropResult == null) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = "Preparing live face sample...",
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                livenessResult = liveness,
            )
        }

        if (!faceCropResult.isSuccess) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = faceCropResult.failureReason.cropFailureMessage(),
                recognitionDecision = RecognitionDecision.ERROR,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
            )
        }

        val poseMessage = sampleTargetMessage(faceDetectionResult)
        if (poseMessage != null) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = poseMessage,
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
            )
        }

        if (frameInfo.timestampNanos - lastSampleCapturedAtNanos < MinimumSampleGapNanos) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = nextSamplePrompt(),
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
            )
        }

        val embedding = faceEmbeddingEngine.generateEmbedding(
            faceCropResult = faceCropResult,
            sourceFrameTimestampNanos = frameInfo.timestampNanos,
        )
        if (!embedding.isSuccess) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = embedding.failureReason.embeddingFailureMessage(),
                recognitionDecision = RecognitionDecision.ERROR,
                stableFaceTrackingResult = tracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
                embeddingResult = embedding,
            )
        }

        capturedSampleCount += 1
        lastSampleCapturedAtNanos = frameInfo.timestampNanos
        recentLivenessSamples.clear()
        stableFaceTracker.reset()
        return output(
            state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
            userMessage = "Sample $capturedSampleCount captured",
            recognitionDecision = RecognitionDecision.MATCH_ACCEPTED,
            stableFaceTrackingResult = tracking,
            faceDetectionResult = faceDetectionResult,
            faceCropResult = faceCropResult,
            livenessResult = liveness,
            embeddingResult = embedding,
        )
    }

    fun close() {
        faceEmbeddingEngine.close()
    }

    private fun sampleTargetMessage(faceDetectionResult: FaceDetectionResult): String? {
        val yaw = faceDetectionResult.selectedPrimaryFace?.headEulerAngleY ?: return "Center your face"
        return when (capturedSampleCount) {
            0 -> if (abs(yaw) <= FrontYawDegrees) null else "Look straight for sample 1"
            1 -> if (abs(yaw) in SlightYawMinDegrees..SlightYawMaxDegrees) null else "Turn slightly left or right"
            2 -> if (abs(yaw) <= FrontYawDegrees) null else "Look straight for sample 3"
            else -> null
        }
    }

    private fun nextSamplePrompt(): String =
        when (capturedSampleCount) {
            0 -> "Hold still for sample 1"
            1 -> "Turn slightly left or right for sample 2"
            2 -> "Look straight for sample 3"
            else -> "Enrollment samples captured"
        }

    private fun rememberLivenessSample(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
    ) {
        if (recentLivenessSamples.size == MaxRecentLivenessSamples) {
            recentLivenessSamples.removeFirst()
        }
        recentLivenessSamples.addLast(
            LivenessFrameSample(
                frameInfo = frameInfo,
                faceDetectionResult = faceDetectionResult,
            ),
        )
    }

    private fun output(
        state: LiveFramePipelineState,
        userMessage: String,
        recognitionDecision: RecognitionDecision,
        stableFaceTrackingResult: StableFaceTrackingResult? = null,
        faceDetectionResult: FaceDetectionResult? = null,
        faceCropResult: FaceCropResult? = null,
        livenessResult: com.schoollog.attendance.ml.liveness.LivenessResult? = null,
        embeddingResult: com.schoollog.attendance.ml.recognition.EmbeddingResult? = null,
    ): LiveFramePipelineOutput =
        LiveFramePipelineOutput(
            state = state,
            userMessage = userMessage,
            processedAtMillis = System.currentTimeMillis(),
            processedFrameCount = processedFrameCount,
            recognitionDecision = recognitionDecision,
            stableFaceTrackingResult = stableFaceTrackingResult,
            faceDetectionResult = faceDetectionResult,
            faceCropResult = faceCropResult,
            livenessResult = livenessResult,
            embeddingResult = embeddingResult,
            faceMatchResult = null,
        )

    private fun com.schoollog.attendance.ml.face.FaceQualityResult.guardMessage(): String =
        when (failureReason) {
            FaceQualityFailureReason.NO_FACE -> "Waiting for student..."
            FaceQualityFailureReason.MULTIPLE_FACES -> "Multiple faces detected, using closest face"
            FaceQualityFailureReason.FACE_TOO_SMALL -> "Move closer"
            FaceQualityFailureReason.FACE_NOT_CENTERED,
            FaceQualityFailureReason.FACE_PARTIALLY_OUTSIDE_FRAME -> "Center your face"
            FaceQualityFailureReason.FACE_TOO_TILTED -> "Look straight"
            FaceQualityFailureReason.LOW_LIGHT_PLACEHOLDER -> "Improve lighting"
            FaceQualityFailureReason.BLUR_PLACEHOLDER -> "Hold still"
            FaceQualityFailureReason.PASSED -> nextSamplePrompt()
        }

    private fun StableFaceTrackerState.toPipelineState(): LiveFramePipelineState =
        when (this) {
            StableFaceTrackerState.WAITING_FOR_FACE -> LiveFramePipelineState.WAITING_FOR_FACE
            StableFaceTrackerState.FACE_DETECTED -> LiveFramePipelineState.FACE_DETECTED
            StableFaceTrackerState.HOLD_STILL -> LiveFramePipelineState.HOLD_STILL
            StableFaceTrackerState.FACE_STABLE -> LiveFramePipelineState.FACE_STABLE
            StableFaceTrackerState.READY_FOR_LIVENESS -> LiveFramePipelineState.READY_FOR_LIVENESS
        }

    private fun StableFaceTrackingResult.guardMessage(): String =
        when (state) {
            StableFaceTrackerState.WAITING_FOR_FACE -> "Waiting for student..."
            StableFaceTrackerState.FACE_DETECTED -> nextSamplePrompt()
            StableFaceTrackerState.HOLD_STILL -> "Hold still..."
            StableFaceTrackerState.FACE_STABLE -> "Face stable"
            StableFaceTrackerState.READY_FOR_LIVENESS -> "Checking liveness..."
        }

    private fun EmbeddingFailureReason.embeddingFailureMessage(): String =
        when (this) {
            EmbeddingFailureReason.MODEL_NOT_FOUND -> "Face model not installed"
            EmbeddingFailureReason.MODEL_LOAD_FAILED -> "Face model failed to load"
            EmbeddingFailureReason.INPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.INPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_SHAPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_TENSOR_TYPE_MISMATCH,
            EmbeddingFailureReason.OUTPUT_SIZE_MISMATCH -> "Face model does not match app configuration"
            EmbeddingFailureReason.INVALID_INPUT -> "Invalid face crop for model"
            EmbeddingFailureReason.INFERENCE_FAILED -> "Face model inference failed"
            EmbeddingFailureReason.INVALID_OUTPUT -> "Face model output invalid"
            EmbeddingFailureReason.SUCCESS -> "Face embedding ready"
        }

    private fun FaceCropFailureReason.cropFailureMessage(): String =
        when (this) {
            FaceCropFailureReason.CROP_OUT_OF_BOUNDS -> "Face crop out of bounds"
            FaceCropFailureReason.FACE_TOO_SMALL -> "Face crop too small"
            FaceCropFailureReason.ROTATION_ERROR -> "Face crop rotation failed"
            FaceCropFailureReason.FRAME_CONVERSION_ERROR -> "Face crop conversion failed"
            FaceCropFailureReason.SUCCESS -> "Face crop ready"
        }

    private companion object {
        const val RequiredSamples = 3
        const val MaxRecentLivenessSamples = 16
        const val MinimumSampleGapNanos = 700_000_000L
        const val FrontYawDegrees = 9f
        const val SlightYawMinDegrees = 2.5f
        const val SlightYawMaxDegrees = 14f
    }
}
