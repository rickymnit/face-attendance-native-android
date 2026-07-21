package com.schoollog.attendance.ml.recognition

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import com.schoollog.attendance.ml.face.FaceCropResult
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter

class TfliteFaceEmbeddingEngine(
    context: Context,
    private val metadata: ModelMetadata = ModelMetadata.DefaultFaceEmbedding,
    private val modelAssetPath: String = ModelAssetPath,
) : FaceEmbeddingEngine {
    private val appContext = context.applicationContext
    private var interpreterResult: Result<Interpreter>? = null

    override fun generateEmbedding(
        faceCropResult: FaceCropResult,
        sourceFrameTimestampNanos: Long,
    ): EmbeddingResult {
        if (!faceCropResult.isSuccess || faceCropResult.bitmap == null) {
            return failure(EmbeddingFailureReason.INVALID_INPUT, sourceFrameTimestampNanos)
        }

        val interpreter = loadInterpreter().getOrElse { throwable ->
            val reason = if (throwable is ModelNotFoundException) {
                EmbeddingFailureReason.MODEL_NOT_FOUND
            } else {
                EmbeddingFailureReason.MODEL_LOAD_FAILED
            }
            return failure(reason, sourceFrameTimestampNanos)
        }

        val validation = runCatching { TfliteModelValidator.validate(interpreter, metadata) }.getOrElse {
            return failure(EmbeddingFailureReason.MODEL_LOAD_FAILED, sourceFrameTimestampNanos)
        }
        if (!validation.isValid) {
            return failure(
                reason = validation.failureReason ?: EmbeddingFailureReason.MODEL_LOAD_FAILED,
                sourceFrameTimestampNanos = sourceFrameTimestampNanos,
            )
        }

        val input = runCatching { preprocess(faceCropResult.bitmap) }.getOrElse {
            return failure(EmbeddingFailureReason.INVALID_INPUT, sourceFrameTimestampNanos)
        }
        val output = Array(1) { FloatArray(metadata.embeddingSize) }
        runCatching {
            interpreter.run(input, output)
        }.getOrElse {
            return failure(EmbeddingFailureReason.INFERENCE_FAILED, sourceFrameTimestampNanos)
        }

        val normalized = l2Normalize(output.first()).getOrElse {
            return failure(EmbeddingFailureReason.INVALID_OUTPUT, sourceFrameTimestampNanos)
        }
        return EmbeddingResult(
            embedding = normalized,
            sourceFrameTimestampNanos = sourceFrameTimestampNanos,
            modelVersion = metadata.modelVersion,
            failureReason = EmbeddingFailureReason.SUCCESS,
            metadata = metadata,
        )
    }

    override fun close() {
        interpreterResult?.getOrNull()?.close()
        interpreterResult = null
    }

    private fun loadInterpreter(): Result<Interpreter> {
        interpreterResult?.let { return it }
        val result = runCatching {
            Interpreter(loadModelFile(), Interpreter.Options().apply { setNumThreads(InterpreterThreadCount) })
        }
        interpreterResult = result
        return result
    }

    private fun loadModelFile(): MappedByteBuffer {
        val descriptor = runCatching { appContext.assets.openFd(modelAssetPath) }
            .getOrElse { throw ModelNotFoundException() }
        return descriptor.use { it.useMappedBuffer() }
    }

    private fun AssetFileDescriptor.useMappedBuffer(): MappedByteBuffer =
        FileInputStream(fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = if (bitmap.width == metadata.inputWidth && bitmap.height == metadata.inputHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, metadata.inputWidth, metadata.inputHeight, true)
        }
        val input = ByteBuffer
            .allocateDirect(1 * metadata.inputWidth * metadata.inputHeight * metadata.inputChannels * BytesPerFloat)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(metadata.inputWidth * metadata.inputHeight)
        resized.getPixels(pixels, 0, metadata.inputWidth, 0, 0, metadata.inputWidth, metadata.inputHeight)
        pixels.forEach { pixel ->
            input.putFloat((((pixel shr 16) and 0xFF) - metadata.normalizationMean) / metadata.normalizationStd)
            input.putFloat((((pixel shr 8) and 0xFF) - metadata.normalizationMean) / metadata.normalizationStd)
            input.putFloat(((pixel and 0xFF) - metadata.normalizationMean) / metadata.normalizationStd)
        }
        input.rewind()
        return input
    }

    private fun l2Normalize(embedding: FloatArray): Result<FloatArray> = runCatching {
        if (embedding.size != metadata.embeddingSize) throw IllegalStateException("Unexpected embedding size")
        if (embedding.any { !it.isFinite() }) throw IllegalStateException("Embedding contains invalid values")
        val norm = sqrt(embedding.fold(0f) { sum, value -> sum + value * value })
        if (!norm.isFinite() || norm <= 0f) throw IllegalStateException("Invalid embedding norm")
        FloatArray(embedding.size) { index -> embedding[index] / norm }
    }

    private fun failure(
        reason: EmbeddingFailureReason,
        sourceFrameTimestampNanos: Long,
    ): EmbeddingResult =
        EmbeddingResult(
            embedding = FloatArray(0),
            sourceFrameTimestampNanos = sourceFrameTimestampNanos,
            modelVersion = metadata.modelVersion,
            failureReason = reason,
            metadata = metadata,
        )

    private class ModelNotFoundException : Exception()

    private companion object {
        const val ModelAssetPath = "models/face_embedding.tflite"
        const val BytesPerFloat = 4
        const val InterpreterThreadCount = 2
    }
}
