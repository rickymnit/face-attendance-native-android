package com.schoollog.attendance.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["schoolId", "erpStudentId"], unique = true)],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val schoolId: String,
    val erpStudentId: String,
    val name: String,
    val className: String,
    val section: String,
    val rollNumber: String,
    val status: String,
    val updatedAt: Long,
)
