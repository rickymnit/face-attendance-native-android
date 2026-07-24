package com.schoollog.attendance.ml.face

import com.schoollog.attendance.camera.domain.CameraFrameInfo
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class FaceDetectionResultTest {
    @Test
    fun oneFaceSelectsThatFace() {
        val face = face(left = 30f, top = 20f, right = 70f, bottom = 80f)

        val result = FaceDetectionResult.fromFaces(
            faces = listOf(face),
            frameInfo = frameInfo(),
            qualityEvaluator = FaceQualityEvaluator(nanoTime = { 0L }, recordMetrics = false),
        )

        assertSame(face, result.selectedPrimaryFace)
        assertEquals(FaceSelectionReason.ONLY_FACE, result.selectionReason)
        assertEquals(1, result.faceCount)
        assertTrue(result.hasExactlyOneFace)
    }

    @Test
    fun multipleFacesSelectLargestBoundingBox() {
        val small = face(trackingId = 1, left = 35f, top = 35f, right = 55f, bottom = 55f)
        val large = face(trackingId = 2, left = 25f, top = 20f, right = 75f, bottom = 85f)
        val medium = face(trackingId = 3, left = 30f, top = 25f, right = 65f, bottom = 70f)

        val result = FaceDetectionResult.fromFaces(
            faces = listOf(small, large, medium),
            frameInfo = frameInfo(),
            qualityEvaluator = FaceQualityEvaluator(nanoTime = { 0L }, recordMetrics = false),
        )

        assertSame(large, result.selectedPrimaryFace)
        assertEquals(listOf(large, medium, small), result.allFaces)
        assertEquals(FaceSelectionReason.LARGEST_FACE_SELECTED, result.selectionReason)
        assertEquals(3, result.faceCount)
        assertTrue(!result.hasExactlyOneFace)
    }

    private fun frameInfo(): CameraFrameInfo = CameraFrameInfo(
        timestampNanos = 1L,
        width = 100,
        height = 100,
        rotationDegrees = 0,
    )

    private fun face(
        trackingId: Int? = 1,
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
