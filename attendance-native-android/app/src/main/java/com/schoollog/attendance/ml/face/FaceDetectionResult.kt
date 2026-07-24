package com.schoollog.attendance.ml.face

import com.schoollog.attendance.camera.domain.CameraFrameInfo

data class FaceDetectionResult(
    val allFaces: List<DetectedFace>,
    val selectedPrimaryFace: DetectedFace?,
    val quality: FaceQualityResult,
    val selectionReason: FaceSelectionReason,
) {
    val faces: List<DetectedFace> = allFaces
    val faceCount: Int = allFaces.size
    val hasExactlyOneFace: Boolean = faceCount == 1
    val hasSelectedPrimaryFace: Boolean = selectedPrimaryFace != null
    val primaryFace: DetectedFace? = selectedPrimaryFace
    val hasMultipleFaces: Boolean = faceCount > 1

    companion object {
        fun fromFaces(
            faces: List<DetectedFace>,
            frameInfo: CameraFrameInfo,
            qualityEvaluator: FaceQualityEvaluator,
        ): FaceDetectionResult {
            val sortedFaces = faces.sortedByDescending { it.boundingBox.area }
            val selectedFace = sortedFaces.firstOrNull()
            return FaceDetectionResult(
                allFaces = sortedFaces,
                selectedPrimaryFace = selectedFace,
                quality = qualityEvaluator.evaluate(selectedFace, frameInfo),
                selectionReason = when {
                    selectedFace == null -> FaceSelectionReason.NO_FACE
                    sortedFaces.size == 1 -> FaceSelectionReason.ONLY_FACE
                    else -> FaceSelectionReason.LARGEST_FACE_SELECTED
                },
            )
        }

        fun noFace(): FaceDetectionResult = FaceDetectionResult(
            allFaces = emptyList(),
            selectedPrimaryFace = null,
            quality = FaceQualityResult(
                qualityPassed = false,
                qualityScore = 0f,
                failureReason = FaceQualityFailureReason.NO_FACE,
            ),
            selectionReason = FaceSelectionReason.NO_FACE,
        )
    }
}
