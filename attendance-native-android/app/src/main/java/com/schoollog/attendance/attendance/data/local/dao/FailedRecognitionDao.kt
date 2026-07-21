package com.schoollog.attendance.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schoollog.attendance.attendance.data.local.entity.FailedRecognitionEntity

@Dao
interface FailedRecognitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(failedRecognition: FailedRecognitionEntity)

    @Query("SELECT * FROM failed_recognitions WHERE schoolId = :schoolId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentFailures(schoolId: String, limit: Int = 50): List<FailedRecognitionEntity>

    @Query("SELECT * FROM failed_recognitions WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC LIMIT :limit")
    suspend fun pendingFailures(limit: Int = 100): List<FailedRecognitionEntity>

    @Query("UPDATE failed_recognitions SET syncStatus = :syncStatus WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<String>, syncStatus: String)
}
