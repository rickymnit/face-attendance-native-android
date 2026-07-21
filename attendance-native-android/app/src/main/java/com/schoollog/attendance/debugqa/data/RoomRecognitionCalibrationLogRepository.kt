package com.schoollog.attendance.debugqa.data

import com.schoollog.attendance.attendance.data.local.dao.RecognitionCalibrationLogDao
import com.schoollog.attendance.attendance.data.local.entity.RecognitionCalibrationLogEntity
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLog
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogDraft
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationSummary
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecognitionCalibrationLogRepository(
    private val dao: RecognitionCalibrationLogDao,
) : RecognitionCalibrationLogRepository {
    override val latestLog: Flow<RecognitionCalibrationLog?> =
        dao.observeLatest().map { it?.toDomain() }

    override suspend fun logAttempt(draft: RecognitionCalibrationLogDraft) {
        dao.insert(
            RecognitionCalibrationLogEntity(
                id = UUID.randomUUID().toString(),
                sessionId = draft.sessionId,
                timestamp = draft.timestamp,
                expectedStudentId = draft.expectedStudentId,
                predictedStudentId = draft.predictedStudentId,
                top1StudentId = draft.top1StudentId,
                top1Score = draft.top1Score,
                top2StudentId = draft.top2StudentId,
                top2Score = draft.top2Score,
                top3StudentId = draft.top3StudentId,
                top3Score = draft.top3Score,
                margin = draft.margin,
                livenessScore = draft.livenessScore,
                qualityScore = draft.qualityScore,
                decision = draft.decision.name,
                failureReason = draft.failureReason,
                recognitionMode = draft.recognitionMode.name,
                inferenceTimeMs = draft.inferenceTimeMs,
                matchingTimeMs = draft.matchingTimeMs,
                totalDecisionTimeMs = draft.totalDecisionTimeMs,
                lightingCondition = draft.lightingCondition.name,
                faceCondition = draft.faceCondition.name,
                testerName = draft.testerName,
                deviceModel = draft.deviceModel,
                androidVersion = draft.androidVersion,
                modelVersion = draft.modelVersion,
                schoolId = draft.schoolId,
                sessionNotes = draft.sessionNotes,
            ),
        )
    }

    override suspend fun exportCsv(limit: Int): String {
        val rows = dao.recent(limit).asReversed()
        return buildString {
            appendLine(
                listOf(
                    "sessionId",
                    "timestamp",
                    "testerName",
                    "deviceModel",
                    "androidVersion",
                    "modelVersion",
                    "schoolId",
                    "sessionNotes",
                    "expectedStudentId",
                    "predictedStudentId",
                    "top1StudentId",
                    "top1Score",
                    "top2StudentId",
                    "top2Score",
                    "top3StudentId",
                    "top3Score",
                    "margin",
                    "livenessScore",
                    "qualityScore",
                    "decision",
                    "failureReason",
                    "recognitionMode",
                    "inferenceTimeMs",
                    "matchingTimeMs",
                    "totalDecisionTimeMs",
                    "lightingCondition",
                    "faceCondition",
                ).joinToString(","),
            )
            rows.forEach { row ->
                appendLine(
                    listOf(
                        row.sessionId.csv(),
                        row.timestamp.toString(),
                        row.testerName.csv(),
                        row.deviceModel.csv(),
                        row.androidVersion.csv(),
                        row.modelVersion.csv(),
                        row.schoolId.csv(),
                        row.sessionNotes.csv(),
                        row.expectedStudentId.csv(),
                        row.predictedStudentId.csv(),
                        row.top1StudentId.csv(),
                        row.top1Score.toString(),
                        row.top2StudentId.csv(),
                        row.top2Score?.toString().orEmpty(),
                        row.top3StudentId.csv(),
                        row.top3Score?.toString().orEmpty(),
                        row.margin?.toString().orEmpty(),
                        row.livenessScore.toString(),
                        row.qualityScore.toString(),
                        row.decision.csv(),
                        row.failureReason.csv(),
                        row.recognitionMode.csv(),
                        row.inferenceTimeMs.toString(),
                        row.matchingTimeMs.toString(),
                        row.totalDecisionTimeMs.toString(),
                        row.lightingCondition.csv(),
                        row.faceCondition.csv(),
                    ).joinToString(","),
                )
            }
        }
    }


    override suspend fun summary(limit: Int): RecognitionCalibrationSummary {
        val rows = dao.recent(limit)
        if (rows.isEmpty()) return RecognitionCalibrationSummary()
        val accepted = rows.filter { it.decision == "MATCH_ACCEPTED" }
        val genuineRows = rows.filter { !it.expectedStudentId.isNullOrBlank() }
        val genuineAccepted = accepted.count { !it.expectedStudentId.isNullOrBlank() && it.expectedStudentId == it.predictedStudentId }
        val wrongAccepted = accepted.count { !it.expectedStudentId.isNullOrBlank() && it.expectedStudentId != it.predictedStudentId }
        val genuineRejected = genuineRows.count { it.decision != "MATCH_ACCEPTED" || it.expectedStudentId != it.predictedStudentId }
        val averageDecisionTime = rows.map { it.totalDecisionTimeMs }.filter { it > 0.0 }.average().takeIf { it.isFinite() } ?: 0.0
        return RecognitionCalibrationSummary(
            genuineAccepted = genuineAccepted,
            genuineRejected = genuineRejected,
            wrongAccepted = wrongAccepted,
            ambiguousRejected = rows.count { it.decision == "AMBIGUOUS_MATCH" },
            lowConfidenceRejected = rows.count { it.decision == "LOW_CONFIDENCE" },
            averageDecisionTimeMs = averageDecisionTime,
        )
    }

    override suspend fun clearDebugOnly() {
        dao.clearDebugOnly()
    }

    private fun RecognitionCalibrationLogEntity.toDomain(): RecognitionCalibrationLog =
        RecognitionCalibrationLog(
            id = id,
            sessionId = sessionId,
            timestamp = timestamp,
            expectedStudentId = expectedStudentId,
            predictedStudentId = predictedStudentId,
            top1StudentId = top1StudentId,
            top1Score = top1Score,
            top2StudentId = top2StudentId,
            top2Score = top2Score,
            top3StudentId = top3StudentId,
            top3Score = top3Score,
            margin = margin,
            livenessScore = livenessScore,
            qualityScore = qualityScore,
            decision = decision,
            failureReason = failureReason,
            recognitionMode = recognitionMode,
            inferenceTimeMs = inferenceTimeMs,
            matchingTimeMs = matchingTimeMs,
            totalDecisionTimeMs = totalDecisionTimeMs,
            lightingCondition = lightingCondition,
            faceCondition = faceCondition,
            testerName = testerName,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            modelVersion = modelVersion,
            schoolId = schoolId,
            sessionNotes = sessionNotes,
        )

    private fun String?.csv(): String =
        "\"" + orEmpty().replace("\"", "\"\"") + "\""
}
