package com.schoollog.attendance.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.schoollog.attendance.attendance.data.local.dao.AttendanceEventDao
import com.schoollog.attendance.attendance.data.local.dao.FaceEmbeddingDao
import com.schoollog.attendance.attendance.data.local.dao.FailedRecognitionDao
import com.schoollog.attendance.attendance.data.local.dao.RecognitionCalibrationLogDao
import com.schoollog.attendance.attendance.data.local.dao.StudentDao
import com.schoollog.attendance.attendance.data.local.entity.AttendanceEventEntity
import com.schoollog.attendance.attendance.data.local.entity.FaceEmbeddingEntity
import com.schoollog.attendance.attendance.data.local.entity.FailedRecognitionEntity
import com.schoollog.attendance.attendance.data.local.entity.RecognitionCalibrationLogEntity
import com.schoollog.attendance.attendance.data.local.entity.StudentEntity

@Database(
    entities = [
        StudentEntity::class,
        FaceEmbeddingEntity::class,
        AttendanceEventEntity::class,
        FailedRecognitionEntity::class,
        RecognitionCalibrationLogEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun attendanceEventDao(): AttendanceEventDao
    abstract fun failedRecognitionDao(): FailedRecognitionDao
    abstract fun recognitionCalibrationLogDao(): RecognitionCalibrationLogDao

    companion object {
        private const val ActiveEmbeddingUniqueIndex = "index_face_embeddings_active_unique_student_model"

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_students_schoolId_erpStudentId` " +
                        "ON `students` (`schoolId`, `erpStudentId`)",
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `face_embeddings_new` (" +
                        "`id` TEXT NOT NULL, " +
                        "`schoolId` TEXT NOT NULL, " +
                        "`erpStudentId` TEXT NOT NULL, " +
                        "`modelVersion` TEXT NOT NULL, " +
                        "`embeddingSize` INTEGER NOT NULL, " +
                        "`embedding` BLOB NOT NULL, " +
                        "`qualityScore` REAL NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `face_embeddings_new` (" +
                        "`id`, `schoolId`, `erpStudentId`, `modelVersion`, `embeddingSize`, `embedding`, " +
                        "`qualityScore`, `source`, `status`, `createdAt`, `updatedAt`) " +
                        "SELECT `id`, `schoolId`, `erpStudentId`, `modelVersion`, 0, X'', " +
                        "`qualityScore`, 'APP_ENROLLMENT', `status`, `updatedAt`, `updatedAt` " +
                        "FROM `face_embeddings`",
                )
                db.execSQL("DROP TABLE `face_embeddings`")
                db.execSQL("ALTER TABLE `face_embeddings_new` RENAME TO `face_embeddings`")
                db.execSQL(
                    "UPDATE `face_embeddings` SET `status` = 'DELETED' " +
                        "WHERE `status` = 'ACTIVE' AND rowid NOT IN (" +
                        "SELECT MAX(rowid) FROM `face_embeddings` " +
                        "WHERE `status` = 'ACTIVE' " +
                        "GROUP BY `schoolId`, `erpStudentId`, `modelVersion`)",
                )
                createFaceEmbeddingIndexes(db)
            }
        }

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createRecognitionCalibrationLogTable(db)
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `failed_recognitions` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING'")
                createFailedRecognitionIndexes(db)
            }
        }

        private val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `sessionId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `totalDecisionTimeMs` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `lightingCondition` TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `faceCondition` TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `testerName` TEXT")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `deviceModel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `androidVersion` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `modelVersion` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `schoolId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recognition_calibration_logs` ADD COLUMN `sessionNotes` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recognition_calibration_logs_sessionId` " +
                        "ON `recognition_calibration_logs` (`sessionId`)",
                )
            }
        }


        private val CreateIndexesCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createFaceEmbeddingIndexes(db)
                createFailedRecognitionIndexes(db)
            }
        }

        private fun createFailedRecognitionIndexes(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_failed_recognitions_syncStatus_timestamp` " +
                    "ON `failed_recognitions` (`syncStatus`, `timestamp`)",
            )
        }

        private fun createRecognitionCalibrationLogTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recognition_calibration_logs` (" +
                    "`id` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`expectedStudentId` TEXT, " +
                    "`predictedStudentId` TEXT, " +
                    "`top1StudentId` TEXT, " +
                    "`top1Score` REAL NOT NULL, " +
                    "`top2StudentId` TEXT, " +
                    "`top2Score` REAL, " +
                    "`top3StudentId` TEXT, " +
                    "`top3Score` REAL, " +
                    "`margin` REAL, " +
                    "`livenessScore` REAL NOT NULL, " +
                    "`qualityScore` REAL NOT NULL, " +
                    "`decision` TEXT NOT NULL, " +
                    "`failureReason` TEXT, " +
                    "`recognitionMode` TEXT NOT NULL, " +
                    "`inferenceTimeMs` REAL NOT NULL, " +
                    "`matchingTimeMs` REAL NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recognition_calibration_logs_timestamp` " +
                    "ON `recognition_calibration_logs` (`timestamp`)",
            )
        }


        private fun createFaceEmbeddingIndexes(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_face_embeddings_schoolId_modelVersion_status` " +
                    "ON `face_embeddings` (`schoolId`, `modelVersion`, `status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_face_embeddings_schoolId_erpStudentId_modelVersion` " +
                    "ON `face_embeddings` (`schoolId`, `erpStudentId`, `modelVersion`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `$ActiveEmbeddingUniqueIndex` " +
                    "ON `face_embeddings` (`schoolId`, `erpStudentId`, `modelVersion`) " +
                    "WHERE `status` = 'ACTIVE'",
            )
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "schoollog-attendance.db",
            )
                .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5, Migration5To6)
                .addCallback(CreateIndexesCallback)
                .build()
    }
}
