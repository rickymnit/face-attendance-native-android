package com.schoollog.attendance.attendance.domain

data class FaceEmbeddingDraft(
    val schoolId: String,
    val erpStudentId: String,
    val modelVersion: String,
    val embedding: FloatArray,
    val qualityScore: Float,
    val source: String,
    val status: String,
)

data class StoredFaceEmbedding(
    val id: String,
    val schoolId: String,
    val erpStudentId: String,
    val modelVersion: String,
    val embeddingSize: Int,
    val embedding: FloatArray,
    val qualityScore: Float,
    val source: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

interface FaceEmbeddingRepository {
    suspend fun getActiveEmbeddingsForSchool(
        schoolId: String,
        modelVersion: String,
    ): List<StoredFaceEmbedding>

    suspend fun upsertEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding

    suspend fun markEmbeddingDeleted(
        schoolId: String,
        erpStudentId: String,
        modelVersion: String,
    )

    suspend fun replaceStudentEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding
}
