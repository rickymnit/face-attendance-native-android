package com.schoollog.attendance.camera.domain

import com.schoollog.attendance.ml.face.DetectedFace
import com.schoollog.attendance.ml.face.FaceBoundingBox
import com.schoollog.attendance.ml.face.FaceDetectionResult
import com.schoollog.attendance.ml.face.FaceQualityFailureReason
import com.schoollog.attendance.ml.face.FaceQualityResult
import com.schoollog.attendance.ml.face.FaceSelectionReason
import kotlin.test.assertEquals
import org.junit.Test

class StableFaceTrackerTest {
    @Test
    fun sameSelectedFaceBecomesReadyForLiveness() {
        val tracker = StableFaceTracker()
        val face = face(trackingId = 1)

        tracker.update(frameInfo(0L), detection(face))
        val result = tracker.update(frameInfo(800_000_000L), detection(face))

        assertEquals(StableFaceTrackerState.READY_FOR_LIVENESS, result.state)
    }

    @Test
    fun primaryFaceChangeResetsStability() {
        val tracker = StableFaceTracker()

        tracker.update(frameInfo(0L), detection(face(trackingId = 1)))
        tracker.update(frameInfo(800_000_000L), detection(face(trackingId = 1)))
        val result = tracker.update(frameInfo(900_000_000L), detection(face(trackingId = 2, left = 24f, right = 76f)))

        assertEquals(StableFaceTrackerState.FACE_DETECTED, result.state)
        assertEquals(0L, result.stableDurationMillis)
    }

    private fun detection(face: DetectedFace): FaceDetectionResult = FaceDetectionResult(
        allFaces = listOf(face),
        selectedPrimaryFace = face,
        quality = FaceQualityResult(
            qualityPassed = true,
            qualityScore = 0.95f,
            failureReason = FaceQualityFailureReason.PASSED,
        ),
        selectionReason = FaceSelectionReason.ONLY_FACE,
    )

    private fun frameInfo(timestampNanos: Long): CameraFrameInfo = CameraFrameInfo(
        timestampNanos = timestampNanos,
        width = 100,
        height = 100,
        rotationDegrees = 0,
    )

    private fun face(
        trackingId: Int?,
        left: Float = 25f,
        right: Float = 75f,
    ): DetectedFace = DetectedFace(
        trackingId = trackingId,
        boundingBox = FaceBoundingBox(left = left, top = 20f, right = right, bottom = 85f),
        confidence = 1f,
        headEulerAngleX = 0f,
        headEulerAngleY = 0f,
        headEulerAngleZ = 0f,
        leftEyeOpenProbability = 0.9f,
        rightEyeOpenProbability = 0.9f,
        smilingProbability = 0f,
    )
}
