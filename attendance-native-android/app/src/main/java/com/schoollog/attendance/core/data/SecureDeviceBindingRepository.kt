package com.schoollog.attendance.core.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.schoollog.attendance.core.common.DeviceBinding
import com.schoollog.attendance.core.common.DeviceBindingRepository
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecureDeviceBindingRepository(
    context: Context,
) : DeviceBindingRepository {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val _deviceBinding = MutableStateFlow(readBinding())
    override val deviceBinding: StateFlow<DeviceBinding?> = _deviceBinding.asStateFlow()

    override fun currentBinding(): DeviceBinding? = _deviceBinding.value

    override fun deviceAccessToken(): String? = currentBinding()?.authToken

    override suspend fun saveBinding(binding: DeviceBinding) {
        preferences.edit()
            .putString(SchoolIdKey, binding.schoolId)
            .putString(SchoolCodeKey, binding.schoolCode)
            .putString(SchoolNameKey, binding.schoolName)
            .putString(DeviceIdKey, binding.deviceId)
            .putString(GateIdKey, binding.gateId)
            .putString(DeviceNameKey, binding.deviceName)
            .putString(AuthTokenKey, encrypt(binding.authToken))
            .putLong(ConfigVersionKey, binding.configVersion)
            .putLong(EmbeddingSyncVersionKey, binding.embeddingSyncVersion)
            .putLong(RegisteredAtKey, binding.registeredAtMillis)
            .putLong(LastHeartbeatAtKey, binding.lastHeartbeatAtMillis ?: MissingTimestamp)
            .putLong(LastAttendanceSyncAtKey, binding.lastAttendanceSyncAtMillis ?: MissingTimestamp)
            .putLong(LastEmbeddingSyncAtKey, binding.lastEmbeddingSyncAtMillis ?: MissingTimestamp)
            .apply()
        _deviceBinding.value = binding
    }

    override suspend fun updateLastHeartbeat(timestampMillis: Long) {
        val current = currentBinding() ?: return
        val updated = current.copy(lastHeartbeatAtMillis = timestampMillis)
        preferences.edit().putLong(LastHeartbeatAtKey, timestampMillis).apply()
        _deviceBinding.value = updated
    }

    override suspend fun updateLastAttendanceSync(timestampMillis: Long) {
        val current = currentBinding() ?: return
        val updated = current.copy(lastAttendanceSyncAtMillis = timestampMillis)
        preferences.edit().putLong(LastAttendanceSyncAtKey, timestampMillis).apply()
        _deviceBinding.value = updated
    }

    override suspend fun updateLastEmbeddingSync(timestampMillis: Long) {
        val current = currentBinding() ?: return
        val updated = current.copy(lastEmbeddingSyncAtMillis = timestampMillis)
        preferences.edit().putLong(LastEmbeddingSyncAtKey, timestampMillis).apply()
        _deviceBinding.value = updated
    }

    override suspend fun updateEmbeddingSyncVersion(version: Long) {
        val current = currentBinding() ?: return
        val updated = current.copy(embeddingSyncVersion = version)
        preferences.edit().putLong(EmbeddingSyncVersionKey, version).apply()
        _deviceBinding.value = updated
    }

    override suspend fun clearBinding() {
        preferences.edit().clear().apply()
        _deviceBinding.value = null
    }

    private fun readBinding(): DeviceBinding? {
        val schoolId = preferences.getString(SchoolIdKey, null) ?: return null
        val deviceId = preferences.getString(DeviceIdKey, null) ?: return null
        val gateId = preferences.getString(GateIdKey, null) ?: return null
        val encryptedToken = preferences.getString(AuthTokenKey, null) ?: return null
        val token = decrypt(encryptedToken).getOrNull() ?: return null
        val lastHeartbeat = preferences.getLong(LastHeartbeatAtKey, MissingTimestamp)
        val lastAttendanceSync = preferences.getLong(LastAttendanceSyncAtKey, MissingTimestamp)
        val lastEmbeddingSync = preferences.getLong(LastEmbeddingSyncAtKey, MissingTimestamp)
        return DeviceBinding(
            schoolId = schoolId,
            schoolCode = preferences.getString(SchoolCodeKey, "").orEmpty(),
            schoolName = preferences.getString(SchoolNameKey, null),
            deviceId = deviceId,
            gateId = gateId,
            deviceName = preferences.getString(DeviceNameKey, android.os.Build.MODEL).orEmpty(),
            authToken = token,
            configVersion = preferences.getLong(ConfigVersionKey, 0L),
            embeddingSyncVersion = preferences.getLong(EmbeddingSyncVersionKey, 0L),
            registeredAtMillis = preferences.getLong(RegisteredAtKey, 0L),
            lastHeartbeatAtMillis = lastHeartbeat.takeUnless { it == MissingTimestamp },
            lastAttendanceSyncAtMillis = lastAttendanceSync.takeUnless { it == MissingTimestamp },
            lastEmbeddingSyncAtMillis = lastEmbeddingSync.takeUnless { it == MissingTimestamp },
        )
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): Result<String> = runCatching {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GcmIvSizeBytes)
        val ciphertext = combined.copyOfRange(GcmIvSizeBytes, combined.size)
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GcmTagSizeBits, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PreferencesName = "device_binding"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "schoollog_gate_device_binding_key"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmIvSizeBytes = 12
        const val GcmTagSizeBits = 128
        const val MissingTimestamp = -1L

        const val SchoolIdKey = "school_id"
        const val SchoolCodeKey = "school_code"
        const val SchoolNameKey = "school_name"
        const val DeviceIdKey = "device_id"
        const val GateIdKey = "gate_id"
        const val DeviceNameKey = "device_name"
        const val AuthTokenKey = "auth_token"
        const val ConfigVersionKey = "config_version"
        const val EmbeddingSyncVersionKey = "embedding_sync_version"
        const val RegisteredAtKey = "registered_at"
        const val LastHeartbeatAtKey = "last_heartbeat_at"
        const val LastAttendanceSyncAtKey = "last_attendance_sync_at"
        const val LastEmbeddingSyncAtKey = "last_embedding_sync_at"
    }
}
