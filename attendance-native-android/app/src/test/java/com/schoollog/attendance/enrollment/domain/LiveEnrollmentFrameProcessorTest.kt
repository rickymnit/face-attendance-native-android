package com.schoollog.attendance.enrollment.domain

import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.ml.face.DetectedFace
import com.schoollog.attendance.ml.face.FaceBoundingBox
import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.face.FaceQualityEvaluator
import com.schoollog.attendance.ml.recognition.EmbeddingFailureReason
import com.schoollog.attendance.ml.recognition.EmbeddingResult
import com.schoollog.attendance.ml.recognition.FaceEmbeddingEngine
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test

class LiveEnrollmentFrameProcessorTest {
    @Test
    fun enrollmentUsesLargestFaceWhenMultipleFacesAreDetected() {
        val processor = LiveEnrollmentFrameProcessor(faceEmbeddingEngine = FakeEmbeddingEngine())
        val selected = face(trackingId = 1, left = 25f, top = 20f, right = 75f, bottom = 85f)
        val background = face(trackingId = 2, left = 5f, top = 5f, right = 20f, bottom = 25f)

        val output = processor.process(
            frameInfo = frameInfo(),
            faceDetectionResult = FaceDetectionResult.fromFaces(
                faces = listOf(background, selected),
                frameInfo = frameInfo(),
                qualityEvaluator = FaceQualityEvaluator(nanoTime = { 0L }, recordMetrics = false),
            ),
        )

        assertNotEquals(RecognitionDecision.MULTIPLE_FACES, output.recognitionDecision)
        assertEquals(selected.boundingBox, output.faceDetectionResult?.selectedPrimaryFace?.boundingBox)
        assertEquals("Hold still for sample 1", output.userMessage)
    }

    private class FakeEmbeddingEngine : FaceEmbeddingEngine {
        override fun generateEmbedding(
            faceCropResult: FaceCropResult,
            sourceFrameTimestampNanos: Long,
        ): EmbeddingResult = EmbeddingResult(
            embedding = FloatArray(0),
            sourceFrameTimestampNanos = sourceFrameTimestampNanos,
            modelVersion = "test",
            failureReason = EmbeddingFailureReason.INVALID_INPUT,
            metadata = ModelMetadata.DefaultFaceEmbedding,
        )
    }

    private fun frameInfo(): CameraFrameInfo = CameraFrameInfo(
        timestampNanos = 1L,
        width = 100,
        height = 100,
        rotationDegrees = 0,
    )

    private fun face(
        trackingId: Int?,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): DetectedFace = DetectedFace(
        trackingId = trackingId,
        boundingBox = FaceBoundingBox(left = left, top = top, right = right, bottom = bottom),
        confidence = 1f,
        headEulerAngleX = 0f,
        headEulerAngleY = 0f,
        headEulerAngleZ = 0f,
        leftEyeOpenProbability = 0.9f,
        rightEyeOpenProbability = 0.9f,
        smilingProbability = 0f,
    )
}
