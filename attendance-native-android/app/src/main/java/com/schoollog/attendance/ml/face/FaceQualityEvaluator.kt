package com.schoollog.attendance.ml.face

import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import kotlin.math.abs
import kotlin.math.hypot

class FaceQualityEvaluator(
    private val nanoTime: () -> Long = System::nanoTime,
    private val recordMetrics: Boolean = true,
) {
    fun evaluate(
        selectedFace: DetectedFace?,
        frameInfo: CameraFrameInfo,
    ): FaceQualityResult {
        val startedAtNanos = nanoTime()
        return try {
            evaluateInternal(selectedFace, frameInfo)
        } finally {
            if (recordMetrics) PerformanceMonitor.recordQualityEvaluation(startedAtNanos)
        }
    }

    fun evaluate(
        faces: List<DetectedFace>,
        frameInfo: CameraFrameInfo,
    ): FaceQualityResult = evaluate(
        selectedFace = faces.maxByOrNull { it.boundingBox.area },
        frameInfo = frameInfo,
    )

    private fun evaluateInternal(
        selectedFace: DetectedFace?,
        frameInfo: CameraFrameInfo,
    ): FaceQualityResult {
        val face = selectedFace ?: return failed(FaceQualityFailureReason.NO_FACE, 0f)
        val frameWidth = frameInfo.analysisWidth.coerceAtLeast(1).toFloat()
        val frameHeight = frameInfo.analysisHeight.coerceAtLeast(1).toFloat()
        val box = face.boundingBox

        if (box.left < FrameEdgeMarginPx || box.top < FrameEdgeMarginPx ||
            box.right > frameWidth - FrameEdgeMarginPx || box.bottom > frameHeight - FrameEdgeMarginPx
        ) {
            return failed(FaceQualityFailureReason.FACE_PARTIALLY_OUTSIDE_FRAME, 0.35f)
        }

        val widthRatio = box.width / frameWidth
        val heightRatio = box.height / frameHeight
        val faceSizeRatio = minOf(widthRatio, heightRatio)
        if (widthRatio < MinimumFaceWidthRatio || heightRatio < MinimumFaceHeightRatio) {
            return failed(FaceQualityFailureReason.FACE_TOO_SMALL, faceSizeRatio.coerceIn(0f, 1f))
        }

        val faceCenterX = (box.left + box.right) / 2f
        val faceCenterY = (box.top + box.bottom) / 2f
        val normalizedOffsetX = abs(faceCenterX - frameWidth / 2f) / frameWidth
        val normalizedOffsetY = abs(faceCenterY - frameHeight / 2f) / frameHeight
        if (normalizedOffsetX > MaxCenterOffsetX || normalizedOffsetY > MaxCenterOffsetY) {
            return failed(FaceQualityFailureReason.FACE_NOT_CENTERED, centerScore(normalizedOffsetX, normalizedOffsetY))
        }

        if (abs(face.headEulerAngleY) > MaxYawDegrees) {
            return failed(FaceQualityFailureReason.FACE_TOO_TILTED, 0.45f)
        }

        if (abs(face.headEulerAngleX) > MaxPitchDegrees || abs(face.headEulerAngleZ) > MaxRollDegrees) {
            return failed(FaceQualityFailureReason.FACE_TOO_TILTED, 0.5f)
        }

        val lightingScore = LightingScorePlaceholder
        if (lightingScore < MinimumLightingScore) {
            return failed(FaceQualityFailureReason.LOW_LIGHT_PLACEHOLDER, lightingScore)
        }

        val blurScore = BlurScorePlaceholder
        if (blurScore < MinimumBlurScore) {
            return failed(FaceQualityFailureReason.BLUR_PLACEHOLDER, blurScore)
        }

        val score = weightedScore(
            sizeScore = (faceSizeRatio / PreferredFaceDimensionRatio).coerceIn(0f, 1f),
            centerScore = centerScore(normalizedOffsetX, normalizedOffsetY),
            poseScore = poseScore(face),
            eyeScore = eyeScore(face),
            lightingScore = lightingScore,
            blurScore = blurScore,
        )
        return FaceQualityResult(
            qualityPassed = true,
            qualityScore = score,
            failureReason = FaceQualityFailureReason.PASSED,
        )
    }

    private fun failed(
        reason: FaceQualityFailureReason,
        score: Float,
    ): FaceQualityResult =
        FaceQualityResult(
            qualityPassed = false,
            qualityScore = score.coerceIn(0f, 1f),
            failureReason = reason,
        )

    private fun centerScore(offsetX: Float, offsetY: Float): Float {
        val normalizedDistance = hypot(offsetX / MaxCenterOffsetX, offsetY / MaxCenterOffsetY)
        return (1f - normalizedDistance / 1.4142f).coerceIn(0f, 1f)
    }

    private fun poseScore(face: DetectedFace): Float {
        val yawScore = 1f - (abs(face.headEulerAngleY) / MaxYawDegrees).coerceIn(0f, 1f)
        val pitchScore = 1f - (abs(face.headEulerAngleX) / MaxPitchDegrees).coerceIn(0f, 1f)
        val rollScore = 1f - (abs(face.headEulerAngleZ) / MaxRollDegrees).coerceIn(0f, 1f)
        return ((yawScore * 0.45f) + (pitchScore * 0.25f) + (rollScore * 0.30f)).coerceIn(0f, 1f)
    }

    private fun eyeScore(face: DetectedFace): Float {
        val probabilities = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability)
        if (probabilities.isEmpty()) return 0.8f
        return probabilities.average().toFloat().coerceIn(0f, 1f)
    }

    private fun weightedScore(
        sizeScore: Float,
        centerScore: Float,
        poseScore: Float,
        eyeScore: Float,
        lightingScore: Float,
        blurScore: Float,
    ): Float =
        ((sizeScore * 0.25f) +
            (centerScore * 0.25f) +
            (poseScore * 0.25f) +
            (eyeScore * 0.05f) +
            (lightingScore * 0.10f) +
            (blurScore * 0.10f)).coerceIn(0f, 1f)

    private companion object {
        const val FrameEdgeMarginPx = 2f
        const val MinimumFaceWidthRatio = 0.18f
        const val MinimumFaceHeightRatio = 0.18f
        const val PreferredFaceDimensionRatio = 0.34f
        const val MaxCenterOffsetX = 0.18f
        const val MaxCenterOffsetY = 0.22f
        const val MaxYawDegrees = 18f
        const val MaxPitchDegrees = 20f
        const val MaxRollDegrees = 18f
        const val LightingScorePlaceholder = 0.85f
        const val BlurScorePlaceholder = 0.85f
        const val MinimumLightingScore = 0.45f
        const val MinimumBlurScore = 0.45f
    }
}
