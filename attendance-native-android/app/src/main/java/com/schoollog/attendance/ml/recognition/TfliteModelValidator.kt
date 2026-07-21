package com.schoollog.attendance.ml.recognition

import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

data class TfliteModelTensorInfo(
    val inputShape: IntArray,
    val inputDataType: DataType,
    val outputShape: IntArray,
    val outputDataType: DataType,
    val outputElementCount: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is TfliteModelTensorInfo &&
            inputShape.contentEquals(other.inputShape) &&
            inputDataType == other.inputDataType &&
            outputShape.contentEquals(other.outputShape) &&
            outputDataType == other.outputDataType &&
            outputElementCount == other.outputElementCount

    override fun hashCode(): Int {
        var result = inputShape.contentHashCode()
        result = 31 * result + inputDataType.hashCode()
        result = 31 * result + outputShape.contentHashCode()
        result = 31 * result + outputDataType.hashCode()
        result = 31 * result + outputElementCount
        return result
    }
}

data class TfliteModelValidationResult(
    val tensorInfo: TfliteModelTensorInfo,
    val failureReason: EmbeddingFailureReason?,
    val message: String?,
) {
    val isValid: Boolean = failureReason == null
}

object TfliteModelValidator {
    fun inspect(interpreter: Interpreter): TfliteModelTensorInfo {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        return TfliteModelTensorInfo(
            inputShape = inputTensor.shape(),
            inputDataType = inputTensor.dataType(),
            outputShape = outputTensor.shape(),
            outputDataType = outputTensor.dataType(),
            outputElementCount = outputTensor.numElements(),
        )
    }

    fun validate(
        interpreter: Interpreter,
        metadata: ModelMetadata,
    ): TfliteModelValidationResult {
        val info = inspect(interpreter)
        val expectedInputShape = intArrayOf(
            BatchSize,
            metadata.inputHeight,
            metadata.inputWidth,
            metadata.inputChannels,
        )
        val expectedOutputShape = intArrayOf(BatchSize, metadata.embeddingSize)

        return when {
            !info.inputShape.contentEquals(expectedInputShape) -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = EmbeddingFailureReason.INPUT_TENSOR_SHAPE_MISMATCH,
                message = "Input tensor shape ${info.inputShape.formatShape()} does not match expected ${expectedInputShape.formatShape()}",
            )
            info.inputDataType != metadata.inputDataType -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = EmbeddingFailureReason.INPUT_TENSOR_TYPE_MISMATCH,
                message = "Input tensor type ${info.inputDataType} does not match expected ${metadata.inputDataType}",
            )
            !info.outputShape.contentEquals(expectedOutputShape) -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = EmbeddingFailureReason.OUTPUT_TENSOR_SHAPE_MISMATCH,
                message = "Output tensor shape ${info.outputShape.formatShape()} does not match expected ${expectedOutputShape.formatShape()}",
            )
            info.outputDataType != DataType.FLOAT32 -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = EmbeddingFailureReason.OUTPUT_TENSOR_TYPE_MISMATCH,
                message = "Output tensor type ${info.outputDataType} does not match expected ${DataType.FLOAT32}",
            )
            info.outputElementCount != metadata.embeddingSize -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = EmbeddingFailureReason.OUTPUT_SIZE_MISMATCH,
                message = "Output element count ${info.outputElementCount} does not match expected ${metadata.embeddingSize}",
            )
            else -> TfliteModelValidationResult(
                tensorInfo = info,
                failureReason = null,
                message = null,
            )
        }
    }

    fun IntArray.formatShape(): String = joinToString(prefix = "[", postfix = "]")

    private const val BatchSize = 1
}
