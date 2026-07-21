package com.schoollog.attendance.ml.recognition

import kotlin.math.abs
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmbeddingByteConverterTest {
    @Test
    fun floatArrayRoundTripPreservesValues() {
        val original = floatArrayOf(0f, 1f, -1f, 0.125f, 42.5f, Float.MIN_VALUE)

        val restored = EmbeddingByteConverter.byteArrayToFloatArray(
            EmbeddingByteConverter.floatArrayToByteArray(original),
        )

        assertContentEquals(original.toList(), restored.toList())
    }

    @Test
    fun emptyFloatArrayRoundTrips() {
        val restored = EmbeddingByteConverter.byteArrayToFloatArray(
            EmbeddingByteConverter.floatArrayToByteArray(FloatArray(0)),
        )

        assertEquals(0, restored.size)
    }

    @Test
    fun serializedSizeIsFourBytesPerFloat() {
        val original = FloatArray(512) { index -> index / 512f }

        val bytes = EmbeddingByteConverter.floatArrayToByteArray(original)

        assertEquals(512 * 4, bytes.size)
        val restored = EmbeddingByteConverter.byteArrayToFloatArray(bytes)
        restored.forEachIndexed { index, value ->
            assertTrue(abs(original[index] - value) < 0.000001f)
        }
    }

    @Test
    fun invalidByteLengthFailsClearly() {
        assertFailsWith<IllegalArgumentException> {
            EmbeddingByteConverter.byteArrayToFloatArray(byteArrayOf(1, 2, 3))
        }
    }
}
