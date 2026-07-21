package com.schoollog.attendance.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schoollog.attendance.attendance.data.local.entity.RecognitionCalibrationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionCalibrationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RecognitionCalibrationLogEntity)

    @Query("SELECT * FROM recognition_calibration_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<RecognitionCalibrationLogEntity?>

    @Query("SELECT * FROM recognition_calibration_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 1_000): List<RecognitionCalibrationLogEntity>

    @Query("DELETE FROM recognition_calibration_logs")
    suspend fun clearDebugOnly()
}
