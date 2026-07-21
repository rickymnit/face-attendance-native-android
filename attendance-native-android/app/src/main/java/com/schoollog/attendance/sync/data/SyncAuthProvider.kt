package com.schoollog.attendance.sync.data

interface SyncAuthProvider {
    fun deviceAccessToken(): String?
}

object EmptySyncAuthProvider : SyncAuthProvider {
    override fun deviceAccessToken(): String? = null
}
