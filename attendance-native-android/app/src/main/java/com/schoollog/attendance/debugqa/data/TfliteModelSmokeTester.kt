package com.schoollog.attendance.debugqa.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.os.SystemClock
import com.schoollog.attendance.debugqa.domain.ModelSmokeTestResult
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.ml.recognition.TfliteModelValidator
import com.schoollog.attendance.ml.recognition.TfliteModelValidator.formatShape
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter

class TfliteModelSmokeTester(
    context: Context,
    private val metadata: ModelMetadata = ModelMetadata.DefaultFaceEmbedding,
    private val modelAssetPath: String = ModelAssetPath,
) {
    private val appContext = context.applicationContext

    fun run(): ModelSmokeTestResult {
        val model = loadModelFile().getOrElse { throwable ->
            return ModelSmokeTestResult(
                modelFound = false,
                modelLoaded = false,
                success = false,
                metadata = metadata,
                averageInferenceTimeMillis = null,
                inputShape = null,
                inputDataType = null,
                outputShape = null,
                outputDataType = null,
                error = throwable.message ?: "Model not found",
            )
        }

        return runCatching {
            Interpreter(model, Interpreter.Options().apply { setNumThreads(InterpreterThreadCount) }).use { interpreter ->
                interpreter.allocateTensors()
                val validation = TfliteModelValidator.validate(interpreter, metadata)
                if (!validation.isValid) {
                    return ModelSmokeTestResult(
                        modelFound = true,
                        modelLoaded = true,
                        success = false,
                        metadata = metadata,
                        averageInferenceTimeMillis = null,
                        inputShape = validation.tensorInfo.inputShape.formatShape(),
                        inputDataType = validation.tensorInfo.inputDataType.name,
                        outputShape = validation.tensorInfo.outputShape.formatShape(),
                        outputDataType = validation.tensorInfo.outputDataType.name,
                        error = validation.message,
                    )
                }

                val output = Array(1) { FloatArray(metadata.embeddingSize) }
                var totalInferenceMillis = 0.0
                repeat(SmokeTestRuns) {
                    val input = dummyInputTensor()
                    val startedAtNanos = SystemClock.elapsedRealtimeNanos()
                    interpreter.run(input, output)
                    totalInferenceMillis += elapsedMillis(startedAtNanos)
                }
                validateAndNormalize(output.first())
                val tensorInfo = validation.tensorInfo
                ModelSmokeTestResult(
                    modelFound = true,
                    modelLoaded = true,
                    success = true,
                    metadata = metadata,
                    averageInferenceTimeMillis = totalInferenceMillis / SmokeTestRuns,
                    inputShape = tensorInfo.inputShape.formatShape(),
                    inputDataType = tensorInfo.inputDataType.name,
                    outputShape = tensorInfo.outputShape.formatShape(),
                    outputDataType = tensorInfo.outputDataType.name,
                    error = null,
                )
            }
        }.getOrElse { throwable ->
            ModelSmokeTestResult(
                modelFound = true,
                modelLoaded = false,
                success = false,
                metadata = metadata,
                averageInferenceTimeMillis = null,
                inputShape = null,
                inputDataType = null,
                outputShape = null,
                outputDataType = null,
                error = throwable.message ?: "Smoke test inference failed",
            )
        }
    }

    private fun loadModelFile(): Result<MappedByteBuffer> = runCatching {
        val descriptor = runCatching { appContext.assets.openFd(modelAssetPath) }
            .getOrElse { throw IllegalStateException("Model not found at assets/$modelAssetPath") }
        descriptor.use { it.useMappedBuffer() }
    }

    private fun AssetFileDescriptor.useMappedBuffer(): MappedByteBuffer =
        FileInputStream(fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

    private fun dummyInputTensor(): ByteBuffer {
        val input = ByteBuffer
            .allocateDirect(1 * metadata.inputWidth * metadata.inputHeight * metadata.inputChannels * BytesPerFloat)
            .order(ByteOrder.nativeOrder())
        repeat(metadata.inputWidth * metadata.inputHeight) { index ->
            val value = ((index % 255) - metadata.normalizationMean) / metadata.normalizationStd
            input.putFloat(value)
            input.putFloat(value * 0.5f)
            input.putFloat(-value)
        }
        input.rewind()
        return input
    }

    private fun validateAndNormalize(output: FloatArray): FloatArray {
        require(output.size == metadata.embeddingSize) {
            "Invalid output size ${output.size}, expected ${metadata.embeddingSize}"
        }
        require(output.all { it.isFinite() }) {
            "Output contains NaN or Infinity"
        }
        val norm = sqrt(output.fold(0f) { sum, value -> sum + value * value })
        require(norm.isFinite() && norm > 0f) {
            "Output norm is invalid"
        }
        return FloatArray(output.size) { index -> output[index] / norm }
    }

    private fun elapsedMillis(startedAtNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos).coerceAtLeast(0L) / NanosPerMillis.toDouble()

    private companion object {
        const val ModelAssetPath = "models/face_embedding.tflite"
        const val BytesPerFloat = 4
        const val InterpreterThreadCount = 2
        const val NanosPerMillis = 1_000_000L
        const val SmokeTestRuns = 3
    }
}
