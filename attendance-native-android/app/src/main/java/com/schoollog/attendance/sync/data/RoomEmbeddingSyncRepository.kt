package com.schoollog.attendance.sync.data

import android.util.Base64
import android.util.Log
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingSourceValues
import com.schoollog.attendance.attendance.data.local.FaceEmbeddingStatusValues
import com.schoollog.attendance.attendance.domain.FaceEmbeddingDraft
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.attendance.domain.StudentRepository
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.ml.recognition.EmbeddingByteConverter
import com.schoollog.attendance.ml.recognition.ModelMetadata
import com.schoollog.attendance.sync.domain.EmbeddingSyncRepository
import com.schoollog.attendance.sync.domain.SyncRunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoomEmbeddingSyncRepository(
    private val deviceBindingRepository: DeviceBindingRepository,
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val studentRepository: StudentRepository,
    private val syncApi: SyncApi,
    private val logger: (String) -> Unit = { message -> Log.e(Tag, message) },
) : EmbeddingSyncRepository {
    private val _lastEmbeddingSyncStatus = MutableStateFlow<SyncRunStatus>(SyncRunStatus.Idle)
    override val lastEmbeddingSyncStatus: StateFlow<SyncRunStatus> = _lastEmbeddingSyncStatus.asStateFlow()

    override suspend fun syncEmbeddingDelta(): SyncRunStatus {
        _lastEmbeddingSyncStatus.value = SyncRunStatus.Running
        val binding = deviceBindingRepository.currentBinding()
        if (binding == null) {
            return publishFailure("Device is not registered")
        }

        val localModelVersion = ModelMetadata.DefaultFaceEmbedding.modelVersion
        val result = syncApi.fetchEmbeddingDelta(
            schoolId = binding.schoolId,
            sinceVersion = binding.embeddingSyncVersion,
            modelVersion = localModelVersion,
        )

        return when (result) {
            is SyncApiResult.Success -> applyDelta(result.data, localModelVersion)
            is SyncApiResult.Error -> publishFailure("Embedding sync failed: ${result.type} ${result.message}")
        }
    }

    private suspend fun applyDelta(
        delta: EmbeddingDeltaResponse,
        localModelVersion: String,
    ): SyncRunStatus {
        if (delta.modelVersion != localModelVersion) {
            val message = "Model/embedding version mismatch"
            logger("$message backend=${delta.modelVersion} local=$localModelVersion")
            return publishFailure(message)
        }

        var appliedCount = 0
        val errors = mutableListOf<String>()

        delta.embeddings.forEach { remote ->
            if (remote.status == FaceEmbeddingStatusValues.Deleted) {
                runCatching {
                    faceEmbeddingRepository.markEmbeddingDeleted(
                        schoolId = delta.schoolId,
                        erpStudentId = remote.erpStudentId,
                        modelVersion = delta.modelVersion,
                    )
                }.onSuccess {
                    appliedCount += 1
                }.onFailure { exception ->
                    errors += "Could not delete embedding for ${remote.erpStudentId}: ${exception.message}"
                }
                return@forEach
            }

            val embedding = remote.decodeEmbeddingOrNull()
            if (embedding == null || embedding.size != remote.embeddingSize) {
                errors += "Invalid embedding for ${remote.erpStudentId}"
                return@forEach
            }
            runCatching {
                studentRepository.upsertRemoteStudent(
                    schoolId = delta.schoolId,
                    erpStudentId = remote.erpStudentId,
                    name = remote.student.name,
                    className = remote.student.className,
                    section = remote.student.section,
                    rollNumber = remote.student.rollNumber,
                    status = remote.student.status,
                    updatedAt = System.currentTimeMillis(),
                )
                faceEmbeddingRepository.replaceStudentEmbedding(
                    FaceEmbeddingDraft(
                        schoolId = delta.schoolId,
                        erpStudentId = remote.erpStudentId,
                        modelVersion = delta.modelVersion,
                        embedding = embedding,
                        qualityScore = remote.qualityScore,
                        source = FaceEmbeddingSourceValues.BulkImport,
                        status = FaceEmbeddingStatusValues.Active,
                    ),
                )
            }.onSuccess {
                appliedCount += 1
            }.onFailure { exception ->
                errors += "Could not apply embedding for ${remote.erpStudentId}: ${exception.message}"
            }
        }

        delta.deletedEmbeddings.forEach { deleted ->
            if (deleted.modelVersion != localModelVersion) return@forEach
            runCatching {
                faceEmbeddingRepository.markEmbeddingDeleted(
                    schoolId = delta.schoolId,
                    erpStudentId = deleted.erpStudentId,
                    modelVersion = deleted.modelVersion,
                )
            }.onSuccess {
                appliedCount += 1
            }.onFailure { exception ->
                errors += "Could not delete embedding for ${deleted.erpStudentId}: ${exception.message}"
            }
        }

        return if (errors.isEmpty()) {
            deviceBindingRepository.updateEmbeddingSyncVersion(delta.toVersion)
            deviceBindingRepository.updateLastEmbeddingSync(System.currentTimeMillis())
            publishSuccess(appliedCount)
        } else {
            logger(errors.joinToString(separator = "; "))
            publishFailure(errors.joinToString(separator = "; "))
        }
    }

    private fun RemoteEmbedding.decodeEmbeddingOrNull(): FloatArray? =
        runCatching {
            val bytes = Base64.decode(embeddingBase64, Base64.DEFAULT)
            EmbeddingByteConverter.byteArrayToFloatArray(bytes)
        }.getOrNull()

    private fun publishSuccess(syncedCount: Int): SyncRunStatus.Success {
        val status = SyncRunStatus.Success(
            syncedCount = syncedCount,
            completedAtMillis = System.currentTimeMillis(),
        )
        _lastEmbeddingSyncStatus.value = status
        return status
    }

    private fun publishFailure(message: String): SyncRunStatus.Failed {
        val status = SyncRunStatus.Failed(
            message = message,
            completedAtMillis = System.currentTimeMillis(),
        )
        _lastEmbeddingSyncStatus.value = status
        return status
    }

    private companion object {
        const val Tag = "EmbeddingSync"
    }
}
