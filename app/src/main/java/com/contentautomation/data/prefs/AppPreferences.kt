package com.contentautomation.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("app_prefs")

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {

        val KEY_SCAN_INTERVAL = intPreferencesKey("scan_interval")
        val KEY_MAX_RETRY = intPreferencesKey("max_retry")
        val KEY_DEFAULT_HASHTAG = stringPreferencesKey("default_hashtag")

        val KEY_TRANSLATION_API_KEY =
            stringPreferencesKey("translation_api_key")

        val KEY_DRIVE_FOLDER_ID =
            stringPreferencesKey("drive_folder_id")


        val KEY_FOREGROUND_ENABLED =
            booleanPreferencesKey("foreground_enabled")
    }

    // ID nhận diện thiết bị MuChi bằng Android ID
    val accountId: String
        get() {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            return if (!androidId.isNullOrBlank()) {
                "MC-${androidId.uppercase()}"
            } else {
                "MC-UNKNOWN"
            }
        }

    val scanIntervalSeconds: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_SCAN_INTERVAL] ?: 600
        }

    val maxRetry: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_MAX_RETRY] ?: 5
        }

    val defaultHashtag: Flow<String> =
        context.dataStore.data.map {
            it[KEY_DEFAULT_HASHTAG] ?: "#thienlaolucqianqian"
        }

    val translationApiKey: Flow<String> =
        context.dataStore.data.map {
            it[KEY_TRANSLATION_API_KEY] ?: ""
        }

    val driveFolderId: Flow<String> =
        context.dataStore.data.map {
            it[KEY_DRIVE_FOLDER_ID] ?: ""
        }


    val foregroundEnabled: Flow<Boolean> =
        context.dataStore.data.map {
            it[KEY_FOREGROUND_ENABLED] ?: false
        }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit {
            it[key] = value
        }
    }

    fun getTranslationApiKeySecure(): String =
        prefs.getString("translation_api_key_secure", null).orEmpty()

    fun setTranslationApiKeySecure(value: String) {
        prefs.edit().putString("translation_api_key_secure", value).apply()
    }}
