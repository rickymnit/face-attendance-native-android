package com.schoollog.attendance.camera.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.schoollog.attendance.ml.face.BoundingBoxFaceAlignment
import com.schoollog.attendance.ml.face.FaceAlignment
import com.schoollog.attendance.ml.face.FaceCropFailureReason
import com.schoollog.attendance.ml.face.FaceCropMetadata
import com.schoollog.attendance.ml.face.FaceCropRequest
import com.schoollog.attendance.ml.face.FaceCropResult
import com.schoollog.attendance.ml.face.FaceCropper
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class CameraXFaceCropper(
    private val faceAlignment: FaceAlignment = BoundingBoxFaceAlignment(),
    private val normalizedCropSize: Int = NormalizedCropSize,
    private val cropExpansionRatio: Float = CropExpansionRatio,
) : FaceCropper {
    override fun cropFace(request: FaceCropRequest): FaceCropResult =
        runCatching { cropFaceInternal(request) }.getOrElse {
            FaceCropResult(
                bitmap = null,
                metadata = null,
                failureReason = FaceCropFailureReason.FRAME_CONVERSION_ERROR,
            )
        }

    private fun cropFaceInternal(request: FaceCropRequest): FaceCropResult {
        if (request.boundingBox.width < MinimumFacePixels || request.boundingBox.height < MinimumFacePixels) {
            return failed(FaceCropFailureReason.FACE_TOO_SMALL)
        }

        val sourceBitmap = request.imageProxy.toCropSourceBitmap()
            ?: return failed(FaceCropFailureReason.FRAME_CONVERSION_ERROR)
        val orientedBitmap = sourceBitmap.orient(
            rotationDegrees = request.rotationDegrees,
            mirror = request.isFrontCameraMirrored,
        ) ?: return failed(FaceCropFailureReason.ROTATION_ERROR)

        val cropRect = expandedCropRect(
            request = request,
            bitmapWidth = orientedBitmap.width,
            bitmapHeight = orientedBitmap.height,
        ) ?: return failed(FaceCropFailureReason.CROP_OUT_OF_BOUNDS)

        if (cropRect.width() < MinimumFacePixels || cropRect.height() < MinimumFacePixels) {
            return failed(FaceCropFailureReason.FACE_TOO_SMALL)
        }

        val cropped = Bitmap.createBitmap(
            orientedBitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height(),
        )
        val normalized = Bitmap.createScaledBitmap(
            cropped,
            normalizedCropSize,
            normalizedCropSize,
            true,
        )
        val metadata = FaceCropMetadata(
            sourceWidth = request.frameWidth,
            sourceHeight = request.frameHeight,
            rotatedWidth = orientedBitmap.width,
            rotatedHeight = orientedBitmap.height,
            rotationDegrees = request.rotationDegrees,
            mirrored = request.isFrontCameraMirrored,
            cropLeft = cropRect.left,
            cropTop = cropRect.top,
            cropWidth = cropRect.width(),
            cropHeight = cropRect.height(),
            normalizedSize = normalizedCropSize,
        )

        val aligned = faceAlignment.align(normalized, metadata)
        return FaceCropResult(
            bitmap = aligned,
            metadata = metadata,
            failureReason = FaceCropFailureReason.SUCCESS,
        )
    }

    private fun expandedCropRect(
        request: FaceCropRequest,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Rect? {
        val box = request.boundingBox
        val width = box.width
        val height = box.height
        val marginX = width * cropExpansionRatio
        val marginY = height * cropExpansionRatio
        val expandedLeft = (box.left - marginX).roundToInt()
        val expandedTop = (box.top - marginY).roundToInt()
        val expandedRight = (box.right + marginX).roundToInt()
        val expandedBottom = (box.bottom + marginY).roundToInt()
        val left = if (request.isFrontCameraMirrored) bitmapWidth - expandedRight else expandedLeft
        val right = if (request.isFrontCameraMirrored) bitmapWidth - expandedLeft else expandedRight
        val top = expandedTop
        val bottom = expandedBottom

        if (right <= left || bottom <= top) return null
        if (left < 0 || top < 0 || right > bitmapWidth || bottom > bitmapHeight) return null

        return Rect(left, top, right, bottom)
    }

    private fun ImageProxy.toCropSourceBitmap(): Bitmap? {
        val nv21 = toNv21() ?: return null
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val output = ByteArrayOutputStream()
        if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), JpegQuality, output)) return null
        val bytes = output.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun ImageProxy.toNv21(): ByteArray? {
        if (planes.size < 3) return null
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val ySize = width * height
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val nv21 = ByteArray(ySize + chromaWidth * chromaHeight * 2)

        copyPlane(
            buffer = yPlane.buffer,
            rowStride = yPlane.rowStride,
            pixelStride = yPlane.pixelStride,
            width = width,
            height = height,
            output = nv21,
            outputOffset = 0,
            outputPixelStride = 1,
        )
        copyPlane(
            buffer = vPlane.buffer,
            rowStride = vPlane.rowStride,
            pixelStride = vPlane.pixelStride,
            width = chromaWidth,
            height = chromaHeight,
            output = nv21,
            outputOffset = ySize,
            outputPixelStride = 2,
        )
        copyPlane(
            buffer = uPlane.buffer,
            rowStride = uPlane.rowStride,
            pixelStride = uPlane.pixelStride,
            width = chromaWidth,
            height = chromaHeight,
            output = nv21,
            outputOffset = ySize + 1,
            outputPixelStride = 2,
        )
        return nv21
    }

    private fun copyPlane(
        buffer: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        output: ByteArray,
        outputOffset: Int,
        outputPixelStride: Int,
    ) {
        val duplicate = buffer.duplicate()
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                val inputIndex = rowStart + col * pixelStride
                val outputIndex = outputOffset + (row * width + col) * outputPixelStride
                if (inputIndex < duplicate.limit() && outputIndex < output.size) {
                    output[outputIndex] = duplicate.get(inputIndex)
                }
            }
        }
    }

    private fun Bitmap.orient(
        rotationDegrees: Int,
        mirror: Boolean,
    ): Bitmap? {
        val normalizedRotation = ((rotationDegrees % FullRotationDegrees) + FullRotationDegrees) % FullRotationDegrees
        if (normalizedRotation % RightAngleDegrees != 0) return null
        if (normalizedRotation == 0 && !mirror) return this

        val matrix = Matrix().apply {
            if (normalizedRotation != 0) postRotate(normalizedRotation.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun failed(reason: FaceCropFailureReason): FaceCropResult =
        FaceCropResult(
            bitmap = null,
            metadata = null,
            failureReason = reason,
        )

    private companion object {
        const val NormalizedCropSize = 160
        const val CropExpansionRatio = 0.20f
        const val MinimumFacePixels = 32f
        const val JpegQuality = 80
        const val FullRotationDegrees = 360
        const val RightAngleDegrees = 90
    }
}
