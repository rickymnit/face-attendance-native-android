package com.schoollog.attendance.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schoollog.attendance.attendance.data.local.entity.AttendanceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AttendanceEventEntity)

    @Query("SELECT COUNT(*) FROM attendance_events WHERE syncStatus = 'PENDING'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM attendance_events WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pendingEvents(limit: Int = 100): List<AttendanceEventEntity>

    @Query("SELECT COUNT(*) FROM attendance_events WHERE syncStatus = 'PENDING'")
    suspend fun pendingSyncCount(): Int

    @Query("UPDATE attendance_events SET syncStatus = :syncStatus WHERE eventId IN (:eventIds)")
    suspend fun updateSyncStatus(eventIds: List<String>, syncStatus: String)

    @Query("DELETE FROM attendance_events")
    suspend fun clearAllDebugOnly()
}
