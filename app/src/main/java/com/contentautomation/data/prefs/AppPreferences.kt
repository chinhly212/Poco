package com.contentautomation.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

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

    // ID tài khoản MuChi
    private val identityPrefs = context.getSharedPreferences(
        "much_identity",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_ACCOUNT_ID = "account_id"

        private val random = SecureRandom()

        fun generateAccountId(): String {
            val chars = "0123456789ABCDEF"
            return buildString {
                repeat(7) {
                    append(chars[random.nextInt(16)])
                }
            }.let { "MC-$it" }
        }

        val KEY_TIKTOK_ACCESS = stringPreferencesKey("tiktok_access")
        val KEY_TIKTOK_REFRESH = stringPreferencesKey("tiktok_refresh")
        val KEY_TIKTOK_EXPIRES = longPreferencesKey("tiktok_expires")
        val KEY_TIKTOK_OPEN_ID = stringPreferencesKey("tiktok_open_id")
        val KEY_SCAN_INTERVAL = intPreferencesKey("scan_interval")
        val KEY_MAX_RETRY = intPreferencesKey("max_retry")
        val KEY_DEFAULT_HASHTAG = stringPreferencesKey("default_hashtag")
        val KEY_TRANSLATION_API_KEY = stringPreferencesKey("translation_api_key")
        val KEY_DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val KEY_TIKTOK_CLIENT_KEY = stringPreferencesKey("tiktok_client_key")
        val KEY_TIKTOK_CLIENT_SECRET = stringPreferencesKey("tiktok_client_secret")
        val KEY_FOREGROUND_ENABLED = booleanPreferencesKey("foreground_enabled")
    }

    // Tự tạo ID nếu chưa có, giữ nguyên ID cũ
    val accountId: String
        get() {
            synchronized(identityPrefs) {
                val existing = identityPrefs.getString(KEY_ACCOUNT_ID, null)

                if (existing != null) {
                    return existing
                }

                val newId = generateAccountId()

                identityPrefs.edit()
                    .putString(KEY_ACCOUNT_ID, newId)
                    .apply()

                return newId
            }
        }

    val scanIntervalSeconds: Flow<Int> =
        context.dataStore.data.map { it[KEY_SCAN_INTERVAL] ?: 600 }

    val maxRetry: Flow<Int> =
        context.dataStore.data.map { it[KEY_MAX_RETRY] ?: 5 }

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

    val tiktokClientKey: Flow<String> =
        context.dataStore.data.map {
            it[KEY_TIKTOK_CLIENT_KEY] ?: ""
        }

    val tiktokClientSecret: Flow<String> =
        context.dataStore.data.map {
            it[KEY_TIKTOK_CLIENT_SECRET] ?: ""
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

    // Secure TikTok token access
    fun getAccessToken(): String? =
        prefs.getString("tiktok_access", null)

    fun setAccessToken(token: String) =
        prefs.edit().putString("tiktok_access", token).apply()

    fun getRefreshToken(): String? =
        prefs.getString("tiktok_refresh", null)

    fun setRefreshToken(token: String) =
        prefs.edit().putString("tiktok_refresh", token).apply()

    fun getTokenExpiresAt(): Long =
        prefs.getLong("tiktok_expires", 0L)

    fun setTokenExpiresAt(ts: Long) =
        prefs.edit().putLong("tiktok_expires", ts).apply()

    fun getOpenId(): String? =
        prefs.getString("tiktok_open_id", null)

    fun setOpenId(id: String) =
        prefs.edit().putString("tiktok_open_id", id).apply()

    fun clearTokens() =
        prefs.edit().clear().apply()
}
