package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.FaceCropFailureReason
import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.face.FaceQualityFailureReason
import com.schoollog.attendance.ml.liveness.LivenessDecision
import com.schoollog.attendance.ml.liveness.LivenessEngine
import com.schoollog.attendance.ml.liveness.LivenessEngineV0
import com.schoollog.attendance.ml.liveness.LivenessFrameSample
import com.schoollog.attendance.ml.recognition.EmbeddingFailureReason
import com.schoollog.attendance.core.common.RecognitionMode
import com.schoollog.attendance.ml.recognition.FaceEmbeddingEngine
import com.schoollog.attendance.ml.recognition.FaceMatchRequest
import com.schoollog.attendance.ml.recognition.FaceMatcher
import com.schoollog.attendance.ml.recognition.PlaceholderFaceEmbeddingEngine
import com.schoollog.attendance.ml.recognition.PlaceholderFaceMatcher
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import java.util.ArrayDeque

class LiveFramePipeline(
    private val livenessEngine: LivenessEngine = LivenessEngineV0(),
    private val faceEmbeddingEngine: FaceEmbeddingEngine = PlaceholderFaceEmbeddingEngine(),
    private val faceMatcher: FaceMatcher = PlaceholderFaceMatcher(),
    private val debugFaceMatcher: FaceMatcher = PlaceholderFaceMatcher(),
    private val debugFaceEmbeddingEngine: FaceEmbeddingEngine = PlaceholderFaceEmbeddingEngine(),
    private val stableFaceTracker: StableFaceTracker = StableFaceTracker(),
    private val allowDebugMockRecognition: Boolean = false,
    private val schoolId: String,
    private val recognitionMode: RecognitionMode = RecognitionMode.Strict,
) : CameraFrameProcessor {
    private val recentLivenessSamples = ArrayDeque<LivenessFrameSample>(MaxRecentLivenessSamples)
    private var processedFrameCount = 0L
    private var bestLiveFrame: CameraFrameInfo? = null

    override fun process(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
        faceCropResult: FaceCropResult?,
    ): LiveFramePipelineOutput {
        processedFrameCount += 1

        if (!faceDetectionResult.hasExactlyOneFace) {
            recentLivenessSamples.clear()
            val stableFaceTracking = updateStableFaceTracker(frameInfo, faceDetectionResult)
            return output(
                state = LiveFramePipelineState.WAITING_FOR_FACE,
                userMessage = faceDetectionResult.quality.guardMessage(),
                recognitionDecision = if (faceDetectionResult.faceCount > 1) {
                    RecognitionDecision.MULTIPLE_FACES
                } else {
                    RecognitionDecision.UNCERTAIN
                },
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        if (!faceDetectionResult.quality.passes) {
            recentLivenessSamples.clear()
            val stableFaceTracking = updateStableFaceTracker(frameInfo, faceDetectionResult)
            return output(
                state = LiveFramePipelineState.FACE_DETECTED,
                userMessage = faceDetectionResult.quality.guardMessage(),
                recognitionDecision = RecognitionDecision.FACE_QUALITY_FAILED,
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        val stableFaceTracking = updateStableFaceTracker(frameInfo, faceDetectionResult)
        if (!stableFaceTracking.isReadyForLiveness) {
            recentLivenessSamples.clear()
            return output(
                state = stableFaceTracking.state.toPipelineState(),
                userMessage = stableFaceTracking.guardMessage(),
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
            )
        }

        bestLiveFrame = frameInfo
        rememberLivenessSample(frameInfo, faceDetectionResult)
        val liveness = livenessEngine.evaluate(recentLivenessSamples.toList())
        if (liveness.decision != LivenessDecision.PASS) {
            return output(
                state = LiveFramePipelineState.CHECKING_LIVENESS,
                userMessage = "Checking liveness...",
                recognitionDecision = if (liveness.decision == LivenessDecision.FAIL) {
                    RecognitionDecision.LIVENESS_FAILED
                } else {
                    RecognitionDecision.UNCERTAIN
                },
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
                livenessResult = liveness,
            )
        }

        if (faceCropResult == null) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = "Preparing face model...",
                recognitionDecision = RecognitionDecision.UNCERTAIN,
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
                livenessResult = liveness,
            )
        }

        if (!faceCropResult.isSuccess) {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = faceCropResult.failureReason.cropFailureMessage(),
                recognitionDecision = RecognitionDecision.ERROR,
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
            )
        }

        val embeddingStartedAtNanos = System.nanoTime()
        val embedding = faceEmbeddingEngine.generateEmbedding(
            faceCropResult = faceCropResult,
            sourceFrameTimestampNanos = (bestLiveFrame ?: frameInfo).timestampNanos,
        )
        val embeddingInferenceTimeMillis = elapsedMillis(embeddingStartedAtNanos)
        PerformanceMonitor.recordEmbeddingInference(embeddingInferenceTimeMillis)
        val embeddingForMatch = if (embedding.isSuccess) {
            embedding
        } else if (allowDebugMockRecognition) {
            debugFaceEmbeddingEngine.generateEmbedding(
                faceCropResult = faceCropResult,
                sourceFrameTimestampNanos = (bestLiveFrame ?: frameInfo).timestampNanos,
            )
        } else {
            return output(
                state = LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER,
                userMessage = embedding.failureReason.embeddingFailureMessage(),
                recognitionDecision = RecognitionDecision.ERROR,
                stableFaceTrackingResult = stableFaceTracking,
                faceDetectionResult = faceDetectionResult,
                faceCropResult = faceCropResult,
                livenessResult = liveness,
                embeddingResult = embedding,
                embeddingInferenceTimeMillis = embeddingInferenceTimeMillis,
            )
        }

        val usedDebugMockEmbedding = !embedding.isSuccess && allowDebugMockRecognition
        val matchRequest = FaceMatchRequest(
            schoolId = schoolId,
            recognitionMode = recognitionMode,
            embedding = embeddingForMatch,
        )
        val match = if (usedDebugMockEmbedding) {
            debugFaceMatcher.match(matchRequest)
        } else {
            faceMatcher.match(matchRequest)
        }
        PerformanceMonitor.recordMatchResult(match)

        val allGatesPassed = faceDetectionResult.hasExactlyOneFace &&
            faceDetectionResult.quality.passes &&
            stableFaceTracking.isReadyForLiveness &&
            liveness.passes &&
            embeddingForMatch.isSuccess &&
            (!usedDebugMockEmbedding || allowDebugMockRecognition) &&
            match.decision == RecognitionDecision.MATCH_ACCEPTED

        return output(
            state = if (allGatesPassed) {
                LiveFramePipelineState.READY_FOR_RECOGNITION_PLACEHOLDER
            } else {
                LiveFramePipelineState.CHECKING_LIVENESS
            },
            userMessage = if (allGatesPassed) "Attendance marked" else match.reason.gateMatcherMessage(match.decision),
            recognitionDecision = match.decision,
            stableFaceTrackingResult = stableFaceTracking,
            faceDetectionResult = faceDetectionResult,
            faceCropResult = faceCropResult,
            livenessResult = liveness,
            embeddingResult = embeddingForMatch,
            embeddingInferenceTimeMillis = embeddingInferenceTimeMillis,
            faceMatchResult = match,
        )
    }



    fun close() {
        faceEmbeddingEngine.close()
        debugFaceEmbeddingEngine.close()
    }


    private fun updateStableFaceTracker(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
    ): StableFaceTrackingResult {
        val startedAtNanos = System.nanoTime()
        return try {
            stableFaceTracker.update(frameInfo, faceDetectionResult)
        } finally {
            PerformanceMonitor.recordStableTracking(startedAtNanos)
        }
    }

    private fun com.schoollog.attendance.ml.face.FaceQualityResult.guardMessage(): String =
        when (failureReason) {
            FaceQualityFailureReason.NO_FACE -> "Waiting for student..."
            FaceQualityFailureReason.MULTIPLE_FACES -> "Only one student at a time"
            FaceQualityFailureReason.FACE_TOO_SMALL -> "Move closer"
            FaceQualityFailureReason.FACE_NOT_CENTERED,
            FaceQualityFailureReason.FACE_PARTIALLY_OUTSIDE_FRAME -> "Center your face"
            FaceQualityFailureReason.FACE_TOO_TILTED -> "Look straight"
            FaceQualityFailureReason.LOW_LIGHT_PLACEHOLDER -> "Improve lighting"
            FaceQualityFailureReason.BLUR_PLACEHOLDER -> "Hold still"
            FaceQualityFailureReason.PASSED -> "Face detected"
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
            StableFaceTrackerState.FACE_DETECTED -> "Face detected"
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

    private fun String.gateMatcherMessage(decision: RecognitionDecision): String =
        when (decision) {
            RecognitionDecision.NO_EMBEDDINGS_LOADED -> "No enrolled students found"
            RecognitionDecision.LOW_CONFIDENCE -> "Low confidence, please contact guard"
            RecognitionDecision.AMBIGUOUS_MATCH -> "Multiple possible matches, manual review required"
            RecognitionDecision.MODEL_VERSION_MISMATCH -> "Face model version mismatch"
            else -> this
        }

    private fun FaceCropFailureReason.cropFailureMessage(): String =
        when (this) {
            FaceCropFailureReason.CROP_OUT_OF_BOUNDS -> "Face crop out of bounds"
            FaceCropFailureReason.FACE_TOO_SMALL -> "Face crop too small"
            FaceCropFailureReason.ROTATION_ERROR -> "Face crop rotation failed"
            FaceCropFailureReason.FRAME_CONVERSION_ERROR -> "Face crop conversion failed"
            FaceCropFailureReason.SUCCESS -> "Face crop ready"
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
        embeddingInferenceTimeMillis: Double? = null,
        faceMatchResult: com.schoollog.attendance.ml.recognition.FaceMatchResult? = null,
    ): LiveFramePipelineOutput {
        val output = LiveFramePipelineOutput(
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
            embeddingInferenceTimeMillis = embeddingInferenceTimeMillis,
            faceMatchResult = faceMatchResult,
        )
        PerformanceMonitor.recordPipelineOutput(output)
        return output
    }


    private fun elapsedMillis(startedAtNanos: Long): Double =
        (System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / NanosPerMillis.toDouble()

    private companion object {
        const val MaxRecentLivenessSamples = 16
        const val NanosPerMillis = 1_000_000L
    }
}
