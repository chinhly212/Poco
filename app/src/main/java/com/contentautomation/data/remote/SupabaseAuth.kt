package com.contentautomation.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?
)

class SupabaseAuth(context: Context) {
    companion object {
        private const val SUPABASE_URL = "https://odxgthsecijktkkubjwf.supabase.co"
        private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_PA474FOyjVttQPBnAfsGAA_nkEVbAdI"
        private const val PREFS_NAME = "supabase_auth"
        private const val ACCESS_KEY = "supabase_access_token"
        private const val REFRESH_KEY = "supabase_refresh_token"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient()
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun currentAccessToken(): String? = prefs.getString(ACCESS_KEY, null)
    fun currentRefreshToken(): String? = prefs.getString(REFRESH_KEY, null)

    suspend fun signUp(email: String, password: String): Result<SupabaseSession?> =
        authenticate("/auth/v1/signup", JSONObject().put("email", email).put("password", password), false)

    suspend fun signIn(email: String, password: String): Result<SupabaseSession> =
        authenticate("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), true)
            .mapCatching { it ?: error("Tài khoản chưa có phiên đăng nhập. Hãy xác nhận email nếu Supabase yêu cầu.") }

    private suspend fun authenticate(
        path: String,
        payload: JSONObject,
        sessionRequired: Boolean
    ): Result<SupabaseSession?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(SUPABASE_URL + path)
                .addHeader("apikey", SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        val json = JSONObject(body)
                        json.optString("msg").ifBlank {
                            json.optString("message").ifBlank { json.optString("error_description") }
                        }
                    }.getOrDefault("")
                    error(message.ifBlank { "Supabase trả lỗi HTTP ${response.code}" })
                }
                val json = JSONObject(body)
                if (!json.has("access_token")) {
                    if (sessionRequired) error("Supabase chưa trả access token.")
                    return@use null
                }
                val user = json.optJSONObject("user") ?: error("Thiếu thông tin user.")
                val session = SupabaseSession(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token"),
                    userId = user.getString("id"),
                    email = user.optString("email").ifBlank { null }
                )
                saveSession(session)
                session
            }
        }
    }

    suspend fun refreshSession(): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            val refresh = currentRefreshToken() ?: error("Chưa có refresh token.")
            val payload = JSONObject().put("refresh_token", refresh)
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SUPABASE_PUBLISHABLE_KEY)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Không thể làm mới phiên đăng nhập (HTTP ${response.code}).")
                val json = JSONObject(body)
                val user = json.optJSONObject("user") ?: error("Thiếu thông tin user.")
                SupabaseSession(
                    json.getString("access_token"),
                    json.getString("refresh_token"),
                    user.getString("id"),
                    user.optString("email").ifBlank { null }
                ).also(::saveSession)
            }
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = currentAccessToken()
            if (!token.isNullOrBlank()) {
                val request = Request.Builder()
                    .url("$SUPABASE_URL/auth/v1/logout")
                    .addHeader("apikey", SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .post("".toRequestBody(null))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Đăng xuất Supabase thất bại (HTTP ${response.code}).")
                }
            }
            clearSession()
        }
    }

    fun clearSession() {
        prefs.edit().remove(ACCESS_KEY).remove(REFRESH_KEY).apply()
    }

    private fun saveSession(session: SupabaseSession) {
        prefs.edit()
            .putString(ACCESS_KEY, session.accessToken)
            .putString(REFRESH_KEY, session.refreshToken)
            .apply()
    }
}
