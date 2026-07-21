package com.schoollog.attendance.ml.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingByteConverter {
    private const val BytesPerFloat = 4

    fun floatArrayToByteArray(values: FloatArray): ByteArray {
        val buffer = ByteBuffer
            .allocate(values.size * BytesPerFloat)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        require(bytes.size % BytesPerFloat == 0) {
            "Embedding byte array size must be divisible by $BytesPerFloat"
        }
        val buffer = ByteBuffer
            .wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BytesPerFloat) { buffer.float }
    }
}
