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

        result.faces.filter { it != result.selectedPrimaryFace }.forEach { face ->
            drawFaceBox(
                face = face,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                mirrorHorizontally = mirrorHorizontally,
                canvasWidth = size.width,
                color = BackgroundFaceColor,
                strokeWidth = BackgroundFaceBoxStrokeWidth,
            )
        }

        val selectedFace = result.selectedPrimaryFace ?: return@Canvas
        val boxColor = if (result.quality.qualityPassed) PassedFaceColor else FailedFaceColor
        drawFaceBox(
            face = selectedFace,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            mirrorHorizontally = mirrorHorizontally,
            canvasWidth = size.width,
            color = boxColor,
            strokeWidth = FaceBoxStrokeWidth,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFaceBox(
    face: com.schoollog.attendance.ml.face.DetectedFace,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    mirrorHorizontally: Boolean,
    canvasWidth: Float,
    color: Color,
    strokeWidth: Float,
) {
    val mappedLeft = face.boundingBox.left * scale + offsetX
    val mappedTop = face.boundingBox.top * scale + offsetY
    val mappedRight = face.boundingBox.right * scale + offsetX
    val mappedBottom = face.boundingBox.bottom * scale + offsetY
    val left = if (mirrorHorizontally) canvasWidth - mappedRight else mappedLeft
    val right = if (mirrorHorizontally) canvasWidth - mappedLeft else mappedRight
    val top = mappedTop
    val bottom = mappedBottom

    if (right > left && bottom > top) {
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = strokeWidth),
        )
    }
}

private val PassedFaceColor = Color(0xFF28D17C)
private val FailedFaceColor = Color(0xFFFFC043)
private val BackgroundFaceColor = Color.White.copy(alpha = 0.55f)
private const val FaceBoxStrokeWidth = 5f
private const val BackgroundFaceBoxStrokeWidth = 2.5f
private const val CenterGuideStrokeWidth = 2f
private const val CenterGuideWidthRatio = 0.52f
private const val CenterGuideHeightRatio = 0.58f
