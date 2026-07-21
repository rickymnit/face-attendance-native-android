package com.schoollog.attendance.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "face_embeddings",
    indices = [
        Index(value = ["schoolId", "modelVersion", "status"]),
        Index(value = ["schoolId", "erpStudentId", "modelVersion"]),
    ],
)
data class FaceEmbeddingEntity(
    @PrimaryKey val id: String,
    val schoolId: String,
    val erpStudentId: String,
    val modelVersion: String,
    val embeddingSize: Int,
    val embedding: ByteArray,
    val qualityScore: Float,
    val source: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)
