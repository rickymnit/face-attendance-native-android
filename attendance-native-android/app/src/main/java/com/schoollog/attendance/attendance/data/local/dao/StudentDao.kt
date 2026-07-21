package com.schoollog.attendance.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schoollog.attendance.attendance.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: StudentEntity)

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND erpStudentId = :erpStudentId LIMIT 1")
    suspend fun findByErpStudentId(schoolId: String, erpStudentId: String): StudentEntity?

    @Query("SELECT * FROM students WHERE schoolId = :schoolId ORDER BY className, section, rollNumber")
    fun observeStudents(schoolId: String): Flow<List<StudentEntity>>
}
