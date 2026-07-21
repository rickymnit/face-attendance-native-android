package com.schoollog.attendance.enrollment.presentation

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.schoollog.attendance.camera.data.CameraFrameAnalyzer
import com.schoollog.attendance.camera.domain.CameraFrameInfo
import com.schoollog.attendance.camera.domain.LiveFramePipelineOutput
import com.schoollog.attendance.enrollment.domain.LiveEnrollmentFrameProcessor
import com.schoollog.attendance.ml.recognition.TfliteFaceEmbeddingEngine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun EnrollmentCameraPreview(
    onFrameProcessed: (CameraFrameInfo, LiveFramePipelineOutput, Double) -> Unit,
    onCameraStatusChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val frameProcessor = remember(context.applicationContext) {
        LiveEnrollmentFrameProcessor(
            faceEmbeddingEngine = TfliteFaceEmbeddingEngine(context.applicationContext),
        )
    }
    val currentOnFrameProcessed by rememberUpdatedState(onFrameProcessed)
    val analyzer = remember(frameProcessor, analyzerExecutor, mainExecutor) {
        CameraFrameAnalyzer(
            frameProcessor = frameProcessor,
            callbackExecutor = analyzerExecutor,
        ) { frameInfo, output, fps ->
            mainExecutor.execute {
                currentOnFrameProcessed(frameInfo, output, fps)
            }
        }
    }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    LaunchedEffect(context, lifecycleOwner, previewView, frameProcessor) {
        onCameraStatusChanged("Starting enrollment camera")
        val cameraProvider = context.awaitCameraProvider()
        cameraProviderState.value = cameraProvider

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analyzerExecutor, analyzer) }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis,
            )
            onCameraStatusChanged("Enrollment camera live")
        } catch (exception: IllegalArgumentException) {
            onCameraStatusChanged("Front camera unavailable")
        } catch (exception: IllegalStateException) {
            onCameraStatusChanged("Camera unavailable")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderState.value?.unbindAll()
            analyzer.close()
            frameProcessor.close()
            analyzerExecutor.shutdown()
        }
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (exception: Exception) {
                    continuation.resumeWithException(exception)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }
