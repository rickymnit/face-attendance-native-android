package com.schoollog.attendance.app

import android.content.Context
import com.schoollog.attendance.attendance.data.RoomAttendanceEventRepository
import com.schoollog.attendance.attendance.data.RoomFaceEmbeddingRepository
import com.schoollog.attendance.attendance.data.RoomFailedRecognitionRepository
import com.schoollog.attendance.attendance.data.RoomStudentRepository
import com.schoollog.attendance.attendance.data.local.AppDatabase
import com.schoollog.attendance.attendance.domain.AttendanceEventRepository
import com.schoollog.attendance.attendance.domain.FaceEmbeddingRepository
import com.schoollog.attendance.attendance.domain.FailedRecognitionRepository
import com.schoollog.attendance.attendance.domain.StudentRepository
import com.schoollog.attendance.core.common.SettingsRepository
import com.schoollog.attendance.core.common.DeviceBindingRepository
import com.schoollog.attendance.core.data.DataStoreSettingsRepository
import com.schoollog.attendance.core.data.SecureDeviceBindingRepository
import com.schoollog.attendance.debugqa.data.RoomRecognitionCalibrationLogRepository
import com.schoollog.attendance.debugqa.data.RoomRecognitionQaRepository
import com.schoollog.attendance.debugqa.domain.RecognitionCalibrationLogRepository
import com.schoollog.attendance.debugqa.domain.RecognitionQaRepository
import com.schoollog.attendance.enrollment.data.RoomEnrollmentRepository
import com.schoollog.attendance.enrollment.domain.EnrollmentRepository
import com.schoollog.attendance.sync.data.RoomAttendanceSyncRepository
import com.schoollog.attendance.sync.data.RoomEmbeddingSyncRepository
import com.schoollog.attendance.sync.data.SyncApi
import com.schoollog.attendance.sync.data.SyncApiFactory
import com.schoollog.attendance.sync.domain.AttendanceSyncRepository
import com.schoollog.attendance.sync.domain.EmbeddingSyncRepository

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    val deviceBindingRepository: DeviceBindingRepository = SecureDeviceBindingRepository(context)
    val syncApi: SyncApi = SyncApiFactory.create(deviceBindingRepository)
    private val database = AppDatabase.create(context)
    private val attendanceEventDao = database.attendanceEventDao()

    val attendanceEventRepository: AttendanceEventRepository =
        RoomAttendanceEventRepository(attendanceEventDao)

    val studentRepository: StudentRepository =
        RoomStudentRepository(database.studentDao())

    val failedRecognitionRepository: FailedRecognitionRepository =
        RoomFailedRecognitionRepository(database.failedRecognitionDao())

    val faceEmbeddingRepository: FaceEmbeddingRepository =
        RoomFaceEmbeddingRepository(database.faceEmbeddingDao())

    val enrollmentRepository: EnrollmentRepository =
        RoomEnrollmentRepository(
            studentDao = database.studentDao(),
            faceEmbeddingRepository = faceEmbeddingRepository,
            settingsRepository = settingsRepository,
        )

    val attendanceSyncRepository: AttendanceSyncRepository =
        RoomAttendanceSyncRepository(
            attendanceEventDao = attendanceEventDao,
            failedRecognitionDao = database.failedRecognitionDao(),
            settingsRepository = settingsRepository,
            deviceBindingRepository = deviceBindingRepository,
            syncApi = syncApi,
        )

    val embeddingSyncRepository: EmbeddingSyncRepository =
        RoomEmbeddingSyncRepository(
            deviceBindingRepository = deviceBindingRepository,
            faceEmbeddingRepository = faceEmbeddingRepository,
            studentRepository = studentRepository,
            syncApi = syncApi,
        )

    val recognitionQaRepository: RecognitionQaRepository =
        RoomRecognitionQaRepository(
            attendanceEventDao = attendanceEventDao,
            faceEmbeddingDao = database.faceEmbeddingDao(),
        )

    val recognitionCalibrationLogRepository: RecognitionCalibrationLogRepository =
        RoomRecognitionCalibrationLogRepository(database.recognitionCalibrationLogDao())
}
