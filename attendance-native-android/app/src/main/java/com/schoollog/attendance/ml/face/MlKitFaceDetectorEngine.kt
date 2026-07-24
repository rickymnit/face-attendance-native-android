package com.schoollog.attendance.ml.face

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import java.util.concurrent.Executor

class MlKitFaceDetectorEngine(
    private val qualityEvaluator: FaceQualityEvaluator = FaceQualityEvaluator(),
) : FaceDetectorEngine {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .setMinFaceSize(MinFaceSizeRatio)
            .build(),
    )

    @ExperimentalGetImage
    override fun detect(
        imageProxy: ImageProxy,
        frameInfo: CameraFrameInfo,
    ): Task<FaceDetectionResult> {
        val faceDetectionStartedAtNanos = System.nanoTime()
        val mediaImage = imageProxy.image
            ?: return Tasks.forResult(FaceDetectionResult.noFace()).also {
                PerformanceMonitor.recordFaceDetection(faceDetectionStartedAtNanos)
            }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        return detector.process(inputImage)
            .addOnCompleteListener(DirectExecutor) {
                PerformanceMonitor.recordFaceDetection(faceDetectionStartedAtNanos)
            }
            .continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Face detection failed")
                task.result.toFaceDetectionResult(frameInfo)
            }
    }

    override fun close() {
        detector.close()
    }

    private fun List<Face>.toFaceDetectionResult(frameInfo: CameraFrameInfo): FaceDetectionResult =
        FaceDetectionResult.fromFaces(
            faces = map { it.toDetectedFace() },
            frameInfo = frameInfo,
            qualityEvaluator = qualityEvaluator,
        )

    private fun Face.toDetectedFace(): DetectedFace {
        val box = boundingBox
        return DetectedFace(
            trackingId = trackingId,
            boundingBox = FaceBoundingBox(
                left = box.left.toFloat(),
                top = box.top.toFloat(),
                right = box.right.toFloat(),
                bottom = box.bottom.toFloat(),
            ),
            confidence = 1f,
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            leftEyeOpenProbability = leftEyeOpenProbability?.takeIf { it >= 0f },
            rightEyeOpenProbability = rightEyeOpenProbability?.takeIf { it >= 0f },
            smilingProbability = smilingProbability?.takeIf { it >= 0f },
        )
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) {
            command.run()
        }
    }

    private companion object {
        const val MinFaceSizeRatio = 0.15f
    }
}
