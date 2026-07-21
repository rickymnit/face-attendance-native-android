package com.schoollog.attendance.ml.recognition

import com.schoollog.attendance.attendance.domain.FaceEmbeddingDraft
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.attendance.domain.StoredFaceEmbedding
import com.schoollog.attendance.core.common.RecognitionMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalFaceMatcherTest {
    @Test
    fun acceptsClearStrictMatchAndReturnsTopThree() {
        val matcher = matcherWith(
            stored("S1", floatArrayOf(1f, 0f, 0f)),
            stored("S2", floatArrayOf(0.5f, 0.5f, 0f)),
            stored("S3", floatArrayOf(0f, 1f, 0f)),
            stored("S4", floatArrayOf(0f, 0f, 1f)),
        )

        val result = matcher.match(request(floatArrayOf(1f, 0f, 0f)))

        assertEquals(RecognitionDecision.MATCH_ACCEPTED, result.decision)
        assertEquals("S1", result.bestStudentId)
        assertEquals(3, result.topMatches.size)
    }

    @Test
    fun rejectsAmbiguousTopScores() {
        val matcher = matcherWith(
            stored("S1", floatArrayOf(1f, 0f)),
            stored("S2", floatArrayOf(0.999f, 0.001f)),
        )

        val result = matcher.match(request(floatArrayOf(1f, 0f)))

        assertEquals(RecognitionDecision.AMBIGUOUS_MATCH, result.decision)
        assertNotNull(result.secondBestScore)
    }

    @Test
    fun rejectsWhenNoActiveEmbeddingsLoaded() {
        val matcher = matcherWith()

        val result = matcher.match(request(floatArrayOf(1f, 0f)))

        assertEquals(RecognitionDecision.NO_EMBEDDINGS_LOADED, result.decision)
    }

    @Test
    fun rejectsStoredEmbeddingSizeMismatchAsModelVersionMismatch() {
        val matcher = matcherWith(
            stored("S1", floatArrayOf(1f, 0f, 0f)),
        )

        val result = matcher.match(request(floatArrayOf(1f, 0f)))

        assertEquals(RecognitionDecision.MODEL_VERSION_MISMATCH, result.decision)
    }

    private fun matcherWith(vararg embeddings: StoredFaceEmbedding): LocalFaceMatcher =
        LocalFaceMatcher(
            faceEmbeddingRepository = FakeFaceEmbeddingRepository(embeddings.toList()),
            logger = {},
            nanoTime = object {
                var value = 0L
                fun next(): Long {
                    value += 1_000_000L
                    return value
                }
            }::next,
        )

    private fun request(embedding: FloatArray): FaceMatchRequest =
        FaceMatchRequest(
            schoolId = SchoolId,
            recognitionMode = RecognitionMode.Strict,
            embedding = EmbeddingResult(
                embedding = embedding,
                sourceFrameTimestampNanos = 1L,
                modelVersion = ModelVersion,
                failureReason = EmbeddingFailureReason.SUCCESS,
                metadata = ModelMetadata.DefaultFaceEmbedding.copy(modelVersion = ModelVersion, embeddingSize = embedding.size),
            ),
        )

    private fun stored(
        studentId: String,
        embedding: FloatArray,
        embeddingSize: Int = embedding.size,
    ): StoredFaceEmbedding =
        StoredFaceEmbedding(
            id = studentId,
            schoolId = SchoolId,
            erpStudentId = studentId,
            modelVersion = ModelVersion,
            embeddingSize = embeddingSize,
            embedding = embedding,
            qualityScore = 0.95f,
            source = "APP_ENROLLMENT",
            status = "ACTIVE",
            createdAt = 1L,
            updatedAt = 1L,
        )

    private class FakeFaceEmbeddingRepository(
        private val embeddings: List<StoredFaceEmbedding>,
    ) : FaceEmbeddingRepository {
        override suspend fun getActiveEmbeddingsForSchool(
            schoolId: String,
            modelVersion: String,
        ): List<StoredFaceEmbedding> = embeddings.filter {
            it.schoolId == schoolId && it.modelVersion == modelVersion && it.status == "ACTIVE"
        }

        override suspend fun upsertEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding =
            error("Not used")

        override suspend fun markEmbeddingDeleted(
            schoolId: String,
            erpStudentId: String,
            modelVersion: String,
        ) = Unit

        override suspend fun replaceStudentEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding =
            error("Not used")
    }

    private companion object {
        const val SchoolId = "SCHOOL_1"
        const val ModelVersion = "test-model-v1"
    }
}
