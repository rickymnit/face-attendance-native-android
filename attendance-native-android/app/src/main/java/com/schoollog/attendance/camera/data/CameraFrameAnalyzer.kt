package com.schoollog.attendance.camera.data

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.CameraFrameProcessor
import com.schoollog.attendance.camera.domain.LiveFramePipelineOutput
import com.schoollog.attendance.camera.domain.LiveFramePipelineState
import com.schoollog.attendance.camera.domain.PerformanceMonitor
import com.schoollog.attendance.ml.face.FaceCropRequest
import com.schoollog.attendance.ml.face.FaceCropper
import com.schoollog.attendance.ml.face.FaceDetectorEngine
import com.schoollog.attendance.ml.face.MlKitFaceDetectorEngine
import com.schoollog.attendance.ml.recognition.RecognitionDecision
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class CameraFrameAnalyzer(
    private val frameProcessor: CameraFrameProcessor,
    private val callbackExecutor: Executor,
    private val faceDetectorEngine: FaceDetectorEngine = MlKitFaceDetectorEngine(),
    private val faceCropper: FaceCropper = CameraXFaceCropper(),
    private val targetFps: Int = TargetFps,
    private val onFrameProcessed: (CameraFrameInfo, LiveFramePipelineOutput, Double) -> Unit,
) : ImageAnalysis.Analyzer {
    private val processingFrame = AtomicBoolean(false)
    private val minimumFrameIntervalMillis = 1_000L / targetFps.coerceIn(MinTargetFps, MaxTargetFps)
    private var lastProcessedAtMillis = 0L
    private var processedFrameCount = 0
    private var fpsWindowStartedAtMillis = SystemClock.elapsedRealtime()
    private var latestProcessedFps = 0.0

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        PerformanceMonitor.markInputFrame(now)
        if (now - lastProcessedAtMillis < minimumFrameIntervalMillis || !processingFrame.compareAndSet(false, true)) {
            PerformanceMonitor.markDroppedFrame()
            imageProxy.close()
            return
        }
        lastProcessedAtMillis = now

        val frameInfo = CameraFrameInfo(
            timestampNanos = imageProxy.imageInfo.timestamp,
            width = imageProxy.width,
            height = imageProxy.height,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
        )
        latestProcessedFps = calculateProcessedFps(now)

        Log.d(
            Tag,
            "processed timestamp=${frameInfo.timestampNanos} width=${frameInfo.width} " +
                "height=${frameInfo.height} rotation=${frameInfo.rotationDegrees}",
        )

        try {
            faceDetectorEngine.detect(imageProxy, frameInfo)
                .addOnCompleteListener(DirectExecutor) { task ->
                    try {
                        callbackExecutor.execute {
                            try {
                                val pipelineOutput = if (task.isSuccessful) {
                                    val faceDetectionResult = task.result
                                    val output = runCatching {
                                        frameProcessor.process(frameInfo, faceDetectionResult)
                                    }.getOrElse {
                                        errorOutput()
                                    }
                                    output.withFaceCropIfReady(imageProxy, frameInfo)
                                } else {
                                    errorOutput()
                                }
                                PerformanceMonitor.markProcessedFrame()
                                runCatching { onFrameProcessed(frameInfo, pipelineOutput, latestProcessedFps) }
                            } finally {
                                imageProxy.close()
                                processingFrame.set(false)
                            }
                        }
                    } catch (exception: RejectedExecutionException) {
                        imageProxy.close()
                        processingFrame.set(false)
                    }
                }
        } catch (exception: RuntimeException) {
            PerformanceMonitor.markProcessedFrame()
            runCatching { onFrameProcessed(frameInfo, errorOutput(), latestProcessedFps) }
            imageProxy.close()
            processingFrame.set(false)
        }
    }



    private fun LiveFramePipelineOutput.withFaceCropIfReady(
        imageProxy: ImageProxy,
        frameInfo: CameraFrameInfo,
    ): LiveFramePipelineOutput {
        val detection = faceDetectionResult ?: return this
        val face = detection.selectedPrimaryFace ?: return this
        val isReadyForCrop = detection.quality.passes && stableFaceTrackingResult?.isReadyForLiveness == true
        if (!isReadyForCrop) return this

        val cropStartedAtNanos = System.nanoTime()
        val cropResult = try {
            faceCropper.cropFace(
                FaceCropRequest(
                imageProxy = imageProxy,
                rotationDegrees = frameInfo.rotationDegrees,
                boundingBox = face.boundingBox,
                frameWidth = frameInfo.analysisWidth,
                frameHeight = frameInfo.analysisHeight,
                    isFrontCameraMirrored = IsFrontCameraMirroredForPreview,
                ),
            )
        } finally {
            PerformanceMonitor.recordFaceCrop(cropStartedAtNanos)
        }
        if (livenessResult?.passes != true) return copy(faceCropResult = cropResult)

        return runCatching {
            frameProcessor.process(
                frameInfo = frameInfo,
                faceDetectionResult = detection,
                faceCropResult = cropResult,
            )
        }.getOrElse {
            copy(faceCropResult = cropResult)
        }
    }

    fun close() {
        faceDetectorEngine.close()
    }

    private fun calculateProcessedFps(nowMillis: Long): Double {
        processedFrameCount += 1
        val elapsedMillis = nowMillis - fpsWindowStartedAtMillis
        if (elapsedMillis >= FpsWindowMillis) {
            latestProcessedFps = processedFrameCount * 1000.0 / elapsedMillis
            processedFrameCount = 0
            fpsWindowStartedAtMillis = nowMillis
        }
        return latestProcessedFps
    }

    private fun errorOutput(): LiveFramePipelineOutput =
        LiveFramePipelineOutput(
            state = LiveFramePipelineState.ERROR,
            userMessage = "Camera pipeline error",
            processedAtMillis = System.currentTimeMillis(),
            processedFrameCount = 0L,
            recognitionDecision = RecognitionDecision.ERROR,
        )

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) {
            command.run()
        }
    }

    private companion object {
        const val Tag = "CameraFrameAnalyzer"
        const val FpsWindowMillis = 1_000L
        const val TargetFps = 8
        const val MinTargetFps = 5
        const val MaxTargetFps = 10
        const val IsFrontCameraMirroredForPreview = false
    }
}
