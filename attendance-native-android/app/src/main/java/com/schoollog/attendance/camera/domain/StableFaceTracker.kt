package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.DetectedFace
import com.schoollog.attendance.ml.face.FaceDetectionResult
import kotlin.math.abs

class StableFaceTracker(
    private val faceStableAfterMillis: Long = FaceStableAfterMillis,
    private val readyForLivenessAfterMillis: Long = ReadyForLivenessAfterMillis,
) {
    private var anchorFace: DetectedFace? = null
    private var stableStartedAtNanos: Long? = null

    fun update(
        frameInfo: CameraFrameInfo,
        faceDetectionResult: FaceDetectionResult,
    ): StableFaceTrackingResult {
        if (!faceDetectionResult.hasSelectedPrimaryFace || !faceDetectionResult.quality.passes) {
            reset()
            return StableFaceTrackingResult(
                state = when {
                    faceDetectionResult.hasSelectedPrimaryFace -> StableFaceTrackerState.FACE_DETECTED
                    else -> StableFaceTrackerState.WAITING_FOR_FACE
                },
                stableDurationMillis = 0L,
            )
        }

        val face = faceDetectionResult.selectedPrimaryFace ?: run {
            reset()
            return StableFaceTrackingResult(StableFaceTrackerState.WAITING_FOR_FACE, 0L)
        }

        val anchor = anchorFace
        if (anchor == null || !isSameStableFace(anchor, face, frameInfo)) {
            anchorFace = face
            stableStartedAtNanos = frameInfo.timestampNanos
            return StableFaceTrackingResult(StableFaceTrackerState.FACE_DETECTED, 0L)
        }

        val stableDurationMillis = ((frameInfo.timestampNanos - (stableStartedAtNanos ?: frameInfo.timestampNanos)) / NanosPerMillis)
            .coerceAtLeast(0L)
        return StableFaceTrackingResult(
            state = when {
                stableDurationMillis >= readyForLivenessAfterMillis -> StableFaceTrackerState.READY_FOR_LIVENESS
                stableDurationMillis >= faceStableAfterMillis -> StableFaceTrackerState.FACE_STABLE
                else -> StableFaceTrackerState.HOLD_STILL
            },
            stableDurationMillis = stableDurationMillis,
        )
    }

    fun reset() {
        anchorFace = null
        stableStartedAtNanos = null
    }

    private fun isSameStableFace(
        anchor: DetectedFace,
        current: DetectedFace,
        frameInfo: CameraFrameInfo,
    ): Boolean {
        if (anchor.trackingId != null && current.trackingId != null && anchor.trackingId != current.trackingId) {
            return false
        }

        val frameWidth = frameInfo.analysisWidth.coerceAtLeast(1).toFloat()
        val frameHeight = frameInfo.analysisHeight.coerceAtLeast(1).toFloat()
        val anchorCenterX = (anchor.boundingBox.left + anchor.boundingBox.right) / 2f
        val anchorCenterY = (anchor.boundingBox.top + anchor.boundingBox.bottom) / 2f
        val currentCenterX = (current.boundingBox.left + current.boundingBox.right) / 2f
        val currentCenterY = (current.boundingBox.top + current.boundingBox.bottom) / 2f
        val movedTooMuch = abs(currentCenterX - anchorCenterX) / frameWidth > MaxCenterMovementRatio ||
            abs(currentCenterY - anchorCenterY) / frameHeight > MaxCenterMovementRatio

        val anchorArea = anchor.boundingBox.width * anchor.boundingBox.height
        val currentArea = current.boundingBox.width * current.boundingBox.height
        val areaChangedTooMuch = if (anchorArea <= 0f) {
            true
        } else {
            abs(currentArea - anchorArea) / anchorArea > MaxAreaChangeRatio
        }

        val angleChangedTooMuch = abs(current.headEulerAngleX - anchor.headEulerAngleX) > MaxAngleDeltaDegrees ||
            abs(current.headEulerAngleY - anchor.headEulerAngleY) > MaxAngleDeltaDegrees ||
            abs(current.headEulerAngleZ - anchor.headEulerAngleZ) > MaxAngleDeltaDegrees

        return !movedTooMuch && !areaChangedTooMuch && !angleChangedTooMuch
    }

    private companion object {
        const val NanosPerMillis = 1_000_000L
        const val FaceStableAfterMillis = 500L
        const val ReadyForLivenessAfterMillis = 700L
        const val MaxCenterMovementRatio = 0.08f
        const val MaxAreaChangeRatio = 0.18f
        const val MaxAngleDeltaDegrees = 8f
    }
}
