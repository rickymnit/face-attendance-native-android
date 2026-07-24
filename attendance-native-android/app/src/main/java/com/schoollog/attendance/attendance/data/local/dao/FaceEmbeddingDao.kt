package com.schoollog.attendance.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingStatusValues
import com.schoollog.attendance.attendance.data.local.entity.FaceEmbeddingEntity

@Dao
interface FaceEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: FaceEmbeddingEntity)

    @Query(
        "SELECT * FROM face_embeddings " +
            "WHERE schoolId = :schoolId AND modelVersion = :modelVersion AND status = :status",
    )
    suspend fun getBySchoolModelAndStatus(
        schoolId: String,
        modelVersion: String,
        status: String = FaceEmbeddingStatusValues.Active,
    ): List<FaceEmbeddingEntity>


    @Query(
        "SELECT COUNT(*) FROM face_embeddings " +
            "WHERE schoolId = :schoolId AND modelVersion = :modelVersion AND status = :status",
    )
    suspend fun activeCountForSchoolModel(
        schoolId: String,
        modelVersion: String,
        status: String = FaceEmbeddingStatusValues.Active,
    ): Int

    @Query(
        "SELECT * FROM face_embeddings " +
            "WHERE schoolId = :schoolId AND erpStudentId = :erpStudentId " +
            "AND modelVersion = :modelVersion AND status = :status LIMIT 1",
    )
    suspend fun findForStudent(
        schoolId: String,
        erpStudentId: String,
        modelVersion: String,
        status: String = FaceEmbeddingStatusValues.Active,
    ): FaceEmbeddingEntity?

    @Query(
        "UPDATE face_embeddings SET status = :deletedStatus, updatedAt = :updatedAt " +
            "WHERE schoolId = :schoolId AND erpStudentId = :erpStudentId " +
            "AND modelVersion = :modelVersion AND status = :activeStatus",
    )
    suspend fun markActiveEmbeddingsDeleted(
        schoolId: String,
        erpStudentId: String,
        modelVersion: String,
        updatedAt: Long,
        deletedStatus: String = FaceEmbeddingStatusValues.Deleted,
        activeStatus: String = FaceEmbeddingStatusValues.Active,
    )

    @Query("DELETE FROM face_embeddings WHERE schoolId = :schoolId")
    suspend fun deleteBySchoolDebugOnly(schoolId: String)

    @Transaction
    suspend fun replaceActiveEmbedding(embedding: FaceEmbeddingEntity) {
        markActiveEmbeddingsDeleted(
            schoolId = embedding.schoolId,
            erpStudentId = embedding.erpStudentId,
            modelVersion = embedding.modelVersion,
            updatedAt = embedding.updatedAt,
        )
        upsert(embedding)
    }
}
