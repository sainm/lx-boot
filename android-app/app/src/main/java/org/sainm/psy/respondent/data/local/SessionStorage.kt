package org.sainm.psy.respondent.data.local

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import org.sainm.psy.respondent.data.model.SessionTokens
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

class SessionStorage(context: Context) {
    private val preferences = context.getSharedPreferences("psy_respondent_session", Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }

    fun readTokens(): SessionTokens? {
        val raw = preferences.getString(KEY_TOKENS, null) ?: return null
        val decoded = runCatching {
            val jsonValue = if (raw.startsWith(ENCRYPTED_PREFIX)) {
                decrypt(raw.removePrefix(ENCRYPTED_PREFIX))
            } else {
                raw
            }
            json.decodeFromString(SessionTokens.serializer(), jsonValue)
        }.getOrNull()
        if (decoded == null) {
            clearTokens()
        } else if (!raw.startsWith(ENCRYPTED_PREFIX)) {
            // Transparently migrate legacy plaintext sessions after a valid read.
            writeTokens(decoded)
        }
        return decoded
    }

    fun writeTokens(tokens: SessionTokens) {
        val encrypted = encrypt(json.encodeToString(SessionTokens.serializer(), tokens))
        preferences.edit().putString(KEY_TOKENS, ENCRYPTED_PREFIX + encrypted).apply()
    }

    fun clearTokens() {
        preferences.edit().remove(KEY_TOKENS).apply()
    }

    fun getOrCreateDeviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "android-${System.currentTimeMillis()}"
        preferences.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun getOrCreateSubmitToken(taskId: Long): String {
        val key = submitTokenKey(taskId)
        val existing = preferences.getString(key, null)?.let { raw ->
            runCatching {
                if (raw.startsWith(ENCRYPTED_PREFIX)) decrypt(raw.removePrefix(ENCRYPTED_PREFIX)) else raw
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        if (existing != null) {
            return existing
        }
        preferences.edit().remove(key).apply()
        val token = UUID.randomUUID().toString()
        preferences.edit().putString(key, ENCRYPTED_PREFIX + encrypt(token)).apply()
        return token
    }

    fun clearSubmitToken(taskId: Long) {
        preferences.edit().remove(submitTokenKey(taskId)).apply()
    }

    fun readAssessmentCursor(taskId: Long): Int =
        preferences.getInt(assessmentCursorKey(taskId), 0).coerceAtLeast(0)

    fun writeAssessmentCursor(taskId: Long, questionIndex: Int) {
        preferences.edit().putInt(assessmentCursorKey(taskId), questionIndex.coerceAtLeast(0)).apply()
    }

    fun clearAssessmentCursor(taskId: Long) {
        preferences.edit().remove(assessmentCursorKey(taskId)).apply()
    }

    private fun submitTokenKey(taskId: Long) = "assessment_submit_token:$taskId"

    private fun assessmentCursorKey(taskId: Long) = "assessment_cursor:$taskId"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_LENGTH_BYTES) { "Invalid encrypted session payload" }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_TOKENS = "session_tokens"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ALIAS = "psy_respondent_session_key"
        const val ENCRYPTED_PREFIX = "v1:"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
