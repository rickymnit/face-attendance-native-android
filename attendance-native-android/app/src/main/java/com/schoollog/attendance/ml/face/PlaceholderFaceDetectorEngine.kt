package com.schoollog.attendance.ml.face

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.schoollog.attendance.camera.domain.CameraFrameInfo

class PlaceholderFaceDetectorEngine(
    private val qualityEvaluator: FaceQualityEvaluator = FaceQualityEvaluator(),
) : FaceDetectorEngine {
    override fun detect(
        imageProxy: ImageProxy,
        frameInfo: CameraFrameInfo,
    ): Task<FaceDetectionResult> {
        val simulatedFrameBucket = (frameInfo.timestampNanos / BucketSizeNanos) % SimulationCycleBuckets
        if (simulatedFrameBucket < NoFaceBuckets) {
            return Tasks.forResult(
                FaceDetectionResult(
                    faces = emptyList(),
                    quality = FaceQualityResult(
                        qualityPassed = false,
                        qualityScore = 0f,
                        failureReason = FaceQualityFailureReason.NO_FACE,
                    ),
                ),
            )
        }

        val face = DetectedFace(
            trackingId = 1,
            boundingBox = FaceBoundingBox(
                left = frameInfo.width * 0.32f,
                top = frameInfo.height * 0.18f,
                right = frameInfo.width * 0.68f,
                bottom = frameInfo.height * 0.72f,
            ),
            confidence = 0.92f,
            headEulerAngleX = 0f,
            headEulerAngleY = 2f,
            headEulerAngleZ = -1f,
            leftEyeOpenProbability = 0.8f,
            rightEyeOpenProbability = 0.82f,
            smilingProbability = 0.1f,
        )
        val quality = if (simulatedFrameBucket >= StableFaceBucket) {
            qualityEvaluator.evaluate(listOf(face), frameInfo)
        } else {
            FaceQualityResult(
                qualityPassed = false,
                qualityScore = 0.54f,
                failureReason = FaceQualityFailureReason.FACE_NOT_CENTERED,
            )
        }
        return Tasks.forResult(
            FaceDetectionResult(
                faces = listOf(face),
                quality = quality,
            ),
        )
    }

    private companion object {
        const val BucketSizeNanos = 125_000_000L
        const val SimulationCycleBuckets = 40L
        const val NoFaceBuckets = 8L
        const val StableFaceBucket = 16L
    }
}
