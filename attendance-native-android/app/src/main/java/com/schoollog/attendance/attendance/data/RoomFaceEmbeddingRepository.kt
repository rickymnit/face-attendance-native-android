package com.schoollog.attendance.attendance.data

import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingSourceValues
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingStatusValues
import com.schoollog.attendance.attendance.data.local.dao.FaceEmbeddingDao
import com.schoollog.attendance.attendance.data.local.entity.FaceEmbeddingEntity
import com.schoollog.attendance.attendance.domain.FaceEmbeddingCacheVersion
import com.schoollog.attendance.attendance.domain.FaceEmbeddingDraft
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.attendance.domain.StoredFaceEmbedding
import com.schoollog.attendance.ml.recognition.EmbeddingByteConverter
import java.util.UUID

class RoomFaceEmbeddingRepository(
    private val faceEmbeddingDao: FaceEmbeddingDao,
) : FaceEmbeddingRepository {
    override suspend fun getActiveEmbeddingsForSchool(
        schoolId: String,
        modelVersion: String,
    ): List<StoredFaceEmbedding> =
        faceEmbeddingDao.getBySchoolModelAndStatus(
            schoolId = schoolId,
            modelVersion = modelVersion,
            status = FaceEmbeddingStatusValues.Active,
        ).asSequence()
            .filter { BuildConfig.DEBUG || it.source != FaceEmbeddingSourceValues.DebugPlaceholder }
            .map { it.toStoredEmbedding() }
            .toList()

    override suspend fun upsertEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding {
        val now = System.currentTimeMillis()
        val entity = draft.toEntity(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
        )
        if (draft.status == FaceEmbeddingStatusValues.Active) {
            faceEmbeddingDao.replaceActiveEmbedding(entity)
        } else {
            faceEmbeddingDao.upsert(entity)
        }
        FaceEmbeddingCacheVersion.markChanged()
        return entity.toStoredEmbedding()
    }

    override suspend fun markEmbeddingDeleted(
        schoolId: String,
        erpStudentId: String,
        modelVersion: String,
    ) {
        faceEmbeddingDao.markActiveEmbeddingsDeleted(
            schoolId = schoolId,
            erpStudentId = erpStudentId,
            modelVersion = modelVersion,
            updatedAt = System.currentTimeMillis(),
        )
        FaceEmbeddingCacheVersion.markChanged()
    }

    override suspend fun replaceStudentEmbedding(draft: FaceEmbeddingDraft): StoredFaceEmbedding {
        val activeDraft = draft.copy(status = FaceEmbeddingStatusValues.Active)
        val now = System.currentTimeMillis()
        val entity = activeDraft.toEntity(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
        )
        faceEmbeddingDao.replaceActiveEmbedding(entity)
        FaceEmbeddingCacheVersion.markChanged()
        return entity.toStoredEmbedding()
    }

    private fun FaceEmbeddingDraft.toEntity(
        id: String,
        createdAt: Long,
        updatedAt: Long,
    ): FaceEmbeddingEntity =
        FaceEmbeddingEntity(
            id = id,
            schoolId = schoolId,
            erpStudentId = erpStudentId,
            modelVersion = modelVersion,
            embeddingSize = embedding.size,
            embedding = EmbeddingByteConverter.floatArrayToByteArray(embedding),
            qualityScore = qualityScore,
            source = source,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun FaceEmbeddingEntity.toStoredEmbedding(): StoredFaceEmbedding =
        StoredFaceEmbedding(
            id = id,
            schoolId = schoolId,
            erpStudentId = erpStudentId,
            modelVersion = modelVersion,
            embeddingSize = embeddingSize,
            embedding = EmbeddingByteConverter.byteArrayToFloatArray(embedding),
            qualityScore = qualityScore,
            source = source,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
