package com.imagecaltracker.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.imagecaltracker.assistant.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persistence for model selection + Gemini API key.
 *
 * - Selected model id lives in a DataStore (mirrors `GoalsRepository` for
 *   goals). Cheap to read as a Flow so composables can observe it.
 * - Gemini API key is a secret, so it lives in EncryptedSharedPreferences.
 *   Reads are synchronous — callers are already off the main thread when
 *   talking to the network.
 */
private val Context.modelSettingsDataStore by preferencesDataStore(name = "model_settings")

class ModelSettingsRepository(private val context: Context) {

    private object Keys {
        val SelectedModelId = stringPreferencesKey("selected_model_id")
    }

    val selectedModelIdFlow: Flow<String> = context.modelSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading model settings", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[Keys.SelectedModelId] ?: ModelCatalog.default.id }

    suspend fun setSelectedModelId(id: String) {
        context.modelSettingsDataStore.edit { prefs ->
            prefs[Keys.SelectedModelId] = id
        }
    }

    // --- Gemini API key ------------------------------------------------------

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getGeminiApiKey(): String? =
        securePrefs.getString(KEY_GEMINI_API, null)?.takeIf { it.isNotBlank() }

    fun setGeminiApiKey(key: String?) {
        securePrefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_GEMINI_API) else putString(KEY_GEMINI_API, key)
            apply()
        }
    }

    companion object {
        private const val TAG = "ModelSettingsRepo"
        private const val SECURE_PREFS_NAME = "secure_model_settings"
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
