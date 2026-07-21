package com.schoollog.attendance.ml.recognition

import android.util.Log
import com.schoollog.attendance.attendance.domain.FaceEmbeddingCacheVersion
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.attendance.domain.StoredFaceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LocalFaceMatcher(
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val logger: (String) -> Unit = { message -> Log.d(Tag, message) },
    private val nanoTime: () -> Long = System::nanoTime,
) : FaceMatcher {
    private val lock = Any()
    private var cache: EmbeddingCache? = null

    override fun match(request: FaceMatchRequest): FaceMatchResult {
        val startedAtNanos = nanoTime()
        if (!request.embedding.isSuccess) {
            return result(
                decision = RecognitionDecision.NO_MATCH,
                reason = "Embedding unavailable",
                startedAtNanos = startedAtNanos,
            )
        }
        if (request.embedding.modelVersion != request.embedding.metadata.modelVersion) {
            return result(
                decision = RecognitionDecision.MODEL_VERSION_MISMATCH,
                reason = "Embedding result metadata is inconsistent",
                startedAtNanos = startedAtNanos,
            )
        }

        val modelVersion = request.embedding.modelVersion
        val loadedCache = loadCacheIfNeeded(
            schoolId = request.schoolId,
            modelVersion = modelVersion,
        )
        if (loadedCache.modelVersion != modelVersion) {
            return result(
                decision = RecognitionDecision.MODEL_VERSION_MISMATCH,
                reason = "Loaded embedding model version does not match query model",
                startedAtNanos = startedAtNanos,
            )
        }
        if (loadedCache.embeddings.isEmpty()) {
            return result(
                decision = RecognitionDecision.NO_EMBEDDINGS_LOADED,
                reason = "No active embeddings loaded for this school and model",
                startedAtNanos = startedAtNanos,
            )
        }

        val query = request.embedding.embedding
        var bestStudentId: String? = null
        var bestScore = Float.NEGATIVE_INFINITY
        var secondStudentId: String? = null
        var secondScore = Float.NEGATIVE_INFINITY
        var thirdStudentId: String? = null
        var thirdScore = Float.NEGATIVE_INFINITY
        var skippedForSizeMismatch = 0

        loadedCache.embeddings.forEach { enrolled ->
            if (enrolled.vector.size != query.size) {
                skippedForSizeMismatch += 1
                return@forEach
            }
            val score = cosineSimilarity(query, enrolled.vector)
            if (score > bestScore) {
                thirdScore = secondScore
                thirdStudentId = secondStudentId
                secondScore = bestScore
                secondStudentId = bestStudentId
                bestScore = score
                bestStudentId = enrolled.studentId
            } else if (score > secondScore) {
                thirdScore = secondScore
                thirdStudentId = secondStudentId
                secondScore = score
                secondStudentId = enrolled.studentId
            } else if (score > thirdScore) {
                thirdScore = score
                thirdStudentId = enrolled.studentId
            }
        }

        val thresholds = RecognitionThresholdConfig.forMode(request.recognitionMode)
        val topMatches = buildList(capacity = 3) {
            bestStudentId?.let { add(FaceMatchCandidate(it, bestScore)) }
            secondStudentId?.let { add(FaceMatchCandidate(it, secondScore)) }
            thirdStudentId?.let { add(FaceMatchCandidate(it, thirdScore)) }
        }
        val decision = when {
            topMatches.isEmpty() && skippedForSizeMismatch > 0 -> RecognitionDecision.MODEL_VERSION_MISMATCH
            topMatches.isEmpty() -> RecognitionDecision.NO_EMBEDDINGS_LOADED
            bestScore < thresholds.acceptanceThreshold -> RecognitionDecision.LOW_CONFIDENCE
            secondStudentId != null && bestScore - secondScore < thresholds.ambiguityGap -> RecognitionDecision.AMBIGUOUS_MATCH
            else -> RecognitionDecision.MATCH_ACCEPTED
        }
        val reason = when (decision) {
            RecognitionDecision.MATCH_ACCEPTED -> "Local match accepted"
            RecognitionDecision.LOW_CONFIDENCE -> "Best score ${bestScore.formatScore()} is below ${thresholds.acceptanceThreshold.formatScore()}"
            RecognitionDecision.AMBIGUOUS_MATCH -> "Top two scores are too close: ${bestScore.formatScore()} vs ${secondScore.formatScore()}"
            RecognitionDecision.MODEL_VERSION_MISMATCH -> "Stored embedding size/model is incompatible with query embedding"
            RecognitionDecision.NO_EMBEDDINGS_LOADED -> "No active embeddings loaded for this school and model"
            else -> "No local match"
        }
        val result = FaceMatchResult(
            bestStudentId = bestStudentId,
            bestScore = bestScore.takeIf { it.isFinite() } ?: 0f,
            secondBestScore = secondScore.takeIf { it.isFinite() },
            decision = decision,
            reason = reason,
            topMatches = topMatches,
            matchingTimeMillis = elapsedMillis(startedAtNanos),
        )
        logger(
            "match school=${request.schoolId} model=$modelVersion count=${loadedCache.embeddings.size} " +
                "decision=${result.decision} score=${result.bestScore.formatScore()} time=${result.matchingTimeMillis.formatMillis()}ms",
        )
        return result
    }

    private fun loadCacheIfNeeded(
        schoolId: String,
        modelVersion: String,
    ): EmbeddingCache {
        val currentVersion = FaceEmbeddingCacheVersion.current()
        synchronized(lock) {
            cache?.takeIf {
                it.schoolId == schoolId &&
                    it.modelVersion == modelVersion &&
                    it.cacheVersion == currentVersion
            }?.let { return it }
        }

        val loaded = runBlocking(Dispatchers.IO) {
            faceEmbeddingRepository.getActiveEmbeddingsForSchool(schoolId, modelVersion)
        }.mapNotNull { it.toEnrolledEmbeddingOrNull() }

        val newCache = EmbeddingCache(
            schoolId = schoolId,
            modelVersion = modelVersion,
            embeddings = loaded,
            lastLoadedAt = System.currentTimeMillis(),
            cacheVersion = currentVersion,
        )
        synchronized(lock) {
            cache = newCache
        }
        logger("loaded ${loaded.size} active embeddings for school=$schoolId model=$modelVersion")
        return newCache
    }

    private fun StoredFaceEmbedding.toEnrolledEmbeddingOrNull(): EnrolledFaceEmbedding? {
        if (embeddingSize <= 0) return null
        if (embedding.any { !it.isFinite() }) return null
        return EnrolledFaceEmbedding(
            studentId = erpStudentId,
            vector = embedding,
        )
    }

    private fun cosineSimilarity(
        query: FloatArray,
        enrolled: FloatArray,
    ): Float {
        var dot = 0f
        var queryNorm = 0f
        var enrolledNorm = 0f
        for (index in query.indices) {
            val queryValue = query[index]
            val enrolledValue = enrolled[index]
            dot += queryValue * enrolledValue
            queryNorm += queryValue * queryValue
            enrolledNorm += enrolledValue * enrolledValue
        }
        if (queryNorm <= 0f || enrolledNorm <= 0f) return Float.NEGATIVE_INFINITY
        return (dot / kotlin.math.sqrt(queryNorm * enrolledNorm)).coerceIn(-1f, 1f)
    }

    private fun result(
        decision: RecognitionDecision,
        reason: String,
        startedAtNanos: Long,
    ): FaceMatchResult =
        FaceMatchResult(
            bestStudentId = null,
            bestScore = 0f,
            secondBestScore = null,
            decision = decision,
            reason = reason,
            topMatches = emptyList(),
            matchingTimeMillis = elapsedMillis(startedAtNanos),
        )

    private data class EmbeddingCache(
        val schoolId: String,
        val modelVersion: String,
        val embeddings: List<EnrolledFaceEmbedding>,
        val lastLoadedAt: Long,
        val cacheVersion: Long,
    )


    private fun elapsedMillis(startedAtNanos: Long): Double =
        (nanoTime() - startedAtNanos).coerceAtLeast(0L) / NanosPerMillis.toDouble()

    private fun Float.formatScore(): String = String.format("%.3f", this)
    private fun Double.formatMillis(): String = String.format("%.2f", this)

    private companion object {
        const val Tag = "LocalFaceMatcher"
        const val NanosPerMillis = 1_000_000L
    }
}
