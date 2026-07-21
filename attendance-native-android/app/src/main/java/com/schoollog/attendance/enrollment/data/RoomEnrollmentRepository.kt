package com.schoollog.attendance.enrollment.data

import com.schoollog.attendance.BuildConfig
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingSourceValues
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingStatusValues
import com.schoollog.attendance.attendance.data.local.dao.StudentDao
import com.schoollog.attendance.attendance.data.local.entity.StudentEntity
import com.schoollog.attendance.attendance.domain.FaceEmbeddingDraft
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.enrollment.domain.EnrollmentForm
import com.schoollog.attendance.enrollment.domain.EnrollmentProfile
import com.schoollog.attendance.enrollment.domain.EnrollmentRepository
import com.schoollog.attendance.enrollment.domain.EnrollmentResult
import com.schoollog.attendance.enrollment.domain.LiveEnrollmentResult
import com.schoollog.attendance.ml.recognition.ModelMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomEnrollmentRepository(
    private val studentDao: StudentDao,
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val settingsRepository: SettingsRepository,
) : EnrollmentRepository {
    override fun observeStudents(schoolId: String): Flow<List<EnrollmentProfile>> =
        studentDao.observeStudents(schoolId).map { students -> students.map { it.toProfile() } }

    override suspend fun searchStudent(
        schoolId: String,
        studentId: String,
    ): EnrollmentProfile? =
        studentDao.findByErpStudentId(schoolId, studentId.trim())?.toProfile()

    override suspend fun saveEnrollment(
        schoolId: String,
        form: EnrollmentForm,
    ): EnrollmentResult {
        val trimmedForm = form.trimmed()
        validate(trimmedForm)?.let { return it }

        val existingStudent = studentDao.findByErpStudentId(
            schoolId = schoolId,
            erpStudentId = trimmedForm.studentId,
        )
        if (existingStudent != null) {
            return EnrollmentResult.DuplicateStudent(trimmedForm.studentId)
        }

        val now = System.currentTimeMillis()
        studentDao.upsert(
            StudentEntity(
                schoolId = schoolId,
                erpStudentId = trimmedForm.studentId,
                name = trimmedForm.name,
                className = trimmedForm.className,
                section = trimmedForm.section,
                rollNumber = trimmedForm.rollNumber,
                status = StudentStatusActive,
                updatedAt = now,
            ),
        )

        val allowDebugPlaceholderEmbedding = BuildConfig.DEBUG && settingsRepository
            .attendanceRules
            .first()
            .allowDebugMockRecognition
        if (allowDebugPlaceholderEmbedding) {
            faceEmbeddingRepository.replaceStudentEmbedding(
                FaceEmbeddingDraft(
                    schoolId = schoolId,
                    erpStudentId = trimmedForm.studentId,
                    modelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion,
                    embedding = placeholderEmbedding(ModelMetadata.DefaultFaceEmbedding.embeddingSize),
                    qualityScore = PlaceholderQualityScore,
                    source = FaceEmbeddingSourceValues.DebugPlaceholder,
                    status = FaceEmbeddingStatusValues.Active,
                ),
            )
        }

        return EnrollmentResult.Saved(
            debugPlaceholderEmbeddingCreated = allowDebugPlaceholderEmbedding,
        )
    }

    override suspend fun hasActiveEmbedding(
        schoolId: String,
        studentId: String,
        modelVersion: String,
    ): Boolean =
        faceEmbeddingRepository.getActiveEmbeddingsForSchool(schoolId, modelVersion)
            .any { it.erpStudentId == studentId.trim() }

    override suspend fun saveLiveEnrollment(
        schoolId: String,
        form: EnrollmentForm,
        embedding: FloatArray,
        modelVersion: String,
        qualityScore: Float,
        allowReEnrollment: Boolean,
    ): LiveEnrollmentResult {
        val trimmedForm = form.trimmed()
        validate(trimmedForm)?.let { return LiveEnrollmentResult.ValidationError(it.message) }

        val existingActiveEmbedding = hasActiveEmbedding(
            schoolId = schoolId,
            studentId = trimmedForm.studentId,
            modelVersion = modelVersion,
        )
        if (existingActiveEmbedding && !allowReEnrollment) {
            return LiveEnrollmentResult.ActiveEmbeddingExists(trimmedForm.studentId)
        }

        val now = System.currentTimeMillis()
        studentDao.upsert(
            StudentEntity(
                schoolId = schoolId,
                erpStudentId = trimmedForm.studentId,
                name = trimmedForm.name,
                className = trimmedForm.className,
                section = trimmedForm.section,
                rollNumber = trimmedForm.rollNumber,
                status = StudentStatusActive,
                updatedAt = now,
            ),
        )

        faceEmbeddingRepository.replaceStudentEmbedding(
            FaceEmbeddingDraft(
                schoolId = schoolId,
                erpStudentId = trimmedForm.studentId,
                modelVersion = modelVersion,
                embedding = embedding,
                qualityScore = qualityScore,
                source = FaceEmbeddingSourceValues.AppEnrollment,
                status = FaceEmbeddingStatusValues.Active,
            ),
        )

        return LiveEnrollmentResult.Saved
    }

    private fun validate(form: EnrollmentForm): EnrollmentResult.ValidationError? =
        when {
            form.studentId.isBlank() -> EnrollmentResult.ValidationError("Student ID is required")
            form.name.isBlank() -> EnrollmentResult.ValidationError("Name is required")
            form.className.isBlank() -> EnrollmentResult.ValidationError("Class is required")
            else -> null
        }

    private fun EnrollmentForm.trimmed(): EnrollmentForm =
        copy(
            studentId = studentId.trim(),
            name = name.trim(),
            className = className.trim(),
            section = section.trim(),
            rollNumber = rollNumber.trim(),
        )

    private fun StudentEntity.toProfile(): EnrollmentProfile =
        EnrollmentProfile(
            studentId = erpStudentId,
            displayName = name,
            className = className,
            section = section,
            rollNumber = rollNumber,
        )

    private fun placeholderEmbedding(size: Int): FloatArray {
        val values = FloatArray(size) { index -> ((index % 31) + 1) / 100f }
        val norm = kotlin.math.sqrt(values.fold(0f) { sum, value -> sum + value * value })
        return FloatArray(size) { index -> values[index] / norm }
    }

    private companion object {
        const val StudentStatusActive = "ACTIVE"
        const val PlaceholderQualityScore = 0.85f
    }
}
