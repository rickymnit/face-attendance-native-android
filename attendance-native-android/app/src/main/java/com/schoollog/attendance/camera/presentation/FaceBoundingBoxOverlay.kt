package com.schoollog.attendance.camera.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.ml.face.FaceDetectionResult
import kotlin.math.max

@Composable
fun FaceBoundingBoxOverlay(
    frameInfo: CameraFrameInfo?,
    faceDetectionResult: FaceDetectionResult?,
    modifier: Modifier = Modifier,
    mirrorHorizontally: Boolean = true,
    showCenterGuide: Boolean = true,
) {
    Canvas(modifier = modifier) {
        val currentFrame = frameInfo ?: return@Canvas
        val sourceWidth = currentFrame.analysisWidth.takeIf { it > 0 }?.toFloat() ?: return@Canvas
        val sourceHeight = currentFrame.analysisHeight.takeIf { it > 0 }?.toFloat() ?: return@Canvas
        val scale = max(size.width / sourceWidth, size.height / sourceHeight)
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f

        if (showCenterGuide) {
            val guideWidth = size.width * CenterGuideWidthRatio
            val guideHeight = size.height * CenterGuideHeightRatio
            drawRect(
                color = Color.White.copy(alpha = 0.42f),
                topLeft = Offset((size.width - guideWidth) / 2f, (size.height - guideHeight) / 2f),
                size = Size(guideWidth, guideHeight),
                style = Stroke(width = CenterGuideStrokeWidth),
            )
        }

        val result = faceDetectionResult ?: return@Canvas
        val boxColor = when {
            result.faceCount > 1 -> MultipleFaceColor
            result.quality.qualityPassed -> PassedFaceColor
            else -> FailedFaceColor
        }

        result.faces.forEach { face ->
            val mappedLeft = face.boundingBox.left * scale + offsetX
            val mappedTop = face.boundingBox.top * scale + offsetY
            val mappedRight = face.boundingBox.right * scale + offsetX
            val mappedBottom = face.boundingBox.bottom * scale + offsetY
            val left = if (mirrorHorizontally) size.width - mappedRight else mappedLeft
            val right = if (mirrorHorizontally) size.width - mappedLeft else mappedRight
            val top = mappedTop
            val bottom = mappedBottom

            if (right > left && bottom > top) {
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = FaceBoxStrokeWidth),
                )
            }
        }
    }
}

private val PassedFaceColor = Color(0xFF28D17C)
private val FailedFaceColor = Color(0xFFFFC043)
private val MultipleFaceColor = Color(0xFFFF4D4D)
private const val FaceBoxStrokeWidth = 5f
private const val CenterGuideStrokeWidth = 2f
private const val CenterGuideWidthRatio = 0.52f
private const val CenterGuideHeightRatio = 0.58f
