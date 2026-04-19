package org.sainm.psy.respondent.data.local

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.Json
import org.sainm.psy.respondent.data.model.SessionTokens

class SessionStorage(context: Context) {
    private val preferences = context.getSharedPreferences("psy_respondent_session", Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver
    private val json = Json { ignoreUnknownKeys = true }

    fun readTokens(): SessionTokens? {
        val raw = preferences.getString(KEY_TOKENS, null) ?: return null
        return runCatching { json.decodeFromString(SessionTokens.serializer(), raw) }.getOrNull()
    }

    fun writeTokens(tokens: SessionTokens) {
        preferences.edit().putString(KEY_TOKENS, json.encodeToString(SessionTokens.serializer(), tokens)).apply()
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

    private companion object {
        const val KEY_TOKENS = "session_tokens"
        const val KEY_DEVICE_ID = "device_id"
    }
}
