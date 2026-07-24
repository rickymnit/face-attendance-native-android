package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.DetectedFace
import com.schoollog.attendance.ml.face.FaceBoundingBox
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.face.FaceQualityEvaluator
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class LiveFramePipelineTest {
    @Test
    fun gatePipelineDoesNotFailOnlyBecauseSmallerBackgroundFacesExist() {
        val pipeline = LiveFramePipeline(schoolId = "school-1")
        val selected = face(trackingId = 1, left = 25f, top = 20f, right = 75f, bottom = 85f)
        val background = face(trackingId = 2, left = 5f, top = 5f, right = 20f, bottom = 25f)

        val output = pipeline.process(
            frameInfo = frameInfo(),
            faceDetectionResult = FaceDetectionResult.fromFaces(
                faces = listOf(background, selected),
                frameInfo = frameInfo(),
                qualityEvaluator = FaceQualityEvaluator(nanoTime = { 0L }, recordMetrics = false),
            ),
        )

        assertTrue(output.faceDetectionResult?.hasSelectedPrimaryFace == true)
        assertEquals(RecognitionDecision.UNCERTAIN, output.recognitionDecision)
    }

    @Test
    fun gatePipelineStillBlocksWhenSelectedFaceQualityFails() {
        val pipeline = LiveFramePipeline(schoolId = "school-1")
        val tooSmallSelected = face(trackingId = 1, left = 48f, top = 48f, right = 55f, bottom = 55f)
        val background = face(trackingId = 2, left = 4f, top = 4f, right = 8f, bottom = 8f)

        val output = pipeline.process(
            frameInfo = frameInfo(),
            faceDetectionResult = FaceDetectionResult.fromFaces(
                faces = listOf(background, tooSmallSelected),
                frameInfo = frameInfo(),
                qualityEvaluator = FaceQualityEvaluator(nanoTime = { 0L }, recordMetrics = false),
            ),
        )

        assertEquals(RecognitionDecision.FACE_QUALITY_FAILED, output.recognitionDecision)
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
