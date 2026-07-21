package com.schoollog.attendance.sync.data

import com.schoollog.attendance.BuildConfig

object SyncApiFactory {
    fun create(authProvider: SyncAuthProvider = EmptySyncAuthProvider): SyncApi {
        if (BuildConfig.DEBUG && BuildConfig.MOCK_SYNC_ENABLED) {
            createDebugMockSyncApi()?.let { return it }
        }
        return RealSyncApi.create(BuildConfig.API_BASE_URL, authProvider)
    }

    private fun createDebugMockSyncApi(): SyncApi? =
        runCatching {
            Class.forName("com.schoollog.attendance.sync.data.MockSyncApi")
                .getDeclaredConstructor()
                .newInstance() as SyncApi
        }.getOrNull()
}
