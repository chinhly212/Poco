package com.contentautomation.data.remote

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String
)

data class DriveAccountInfo(
    val email: String,
    val usedBytes: Long,
    val totalBytes: Long
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
}

class GoogleDriveClient(context: Context) {
    private val appContext = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val securePrefs by lazy {
        val key = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "nichi_google_drive",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveAccessToken(token: String) {
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis()).apply()
    }

    fun clearAccessToken() {
        securePrefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_SAVED_AT).apply()
    }

    fun hasSavedToken(): Boolean {
        val token = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        val savedAt = securePrefs.getLong(KEY_SAVED_AT, 0L)
        return !token.isNullOrBlank() && System.currentTimeMillis() - savedAt < TOKEN_LIFETIME_MS
    }

    private fun token(): String {
        val value = securePrefs.getString(KEY_ACCESS_TOKEN, null)
            ?: error("Chưa kết nối Google Drive.")
        val savedAt = securePrefs.getLong(KEY_SAVED_AT, 0L)
        if (System.currentTimeMillis() - savedAt >= TOKEN_LIFETIME_MS) {
            clearAccessToken()
            error("Phiên Google Drive đã hết hạn. Hãy kết nối lại.")
        }
        return value
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${token()}").get().build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Google API HTTP ${response.code}: ${body.take(250)}")
            JSONObject(body)
        }
    }

    suspend fun accountInfo(): DriveAccountInfo {
        val user = getJson("https://www.googleapis.com/oauth2/v3/userinfo")
        val quota = getJson("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
            .optJSONObject("storageQuota") ?: JSONObject()
        return DriveAccountInfo(
            email = user.optString("email", "Google Account"),
            usedBytes = quota.optString("usage").toLongOrNull() ?: 0L,
            totalBytes = quota.optString("limit").toLongOrNull() ?: 0L
        )
    }

    suspend fun listFiles(): List<DriveFileItem> {
        val result = getJson(
            "https://www.googleapis.com/drive/v3/files?spaces=drive&pageSize=100&orderBy=modifiedTime%20desc&fields=files(id,name,mimeType,size,modifiedTime)&q=trashed%3Dfalse"
        )
        val files = result.optJSONArray("files") ?: org.json.JSONArray()
        return (0 until files.length()).mapNotNull { index ->
            val item = files.optJSONObject(index) ?: return@mapNotNull null
            DriveFileItem(
                item.optString("id"),
                item.optString("name"),
                item.optString("mimeType"),
                item.optString("size").toLongOrNull() ?: 0L,
                item.optString("modifiedTime")
            )
        }
    }

    suspend fun upload(uri: Uri, displayName: String, mimeType: String): DriveFileItem =
        withContext(Dispatchers.IO) {
            val length = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?: -1L
            if (length <= 0L) error("Không xác định được dung lượng tệp để tải lên.")
            val metadata = JSONObject().put("name", displayName).put("mimeType", mimeType)
            val initiate = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,mimeType,size,modifiedTime")
                .header("Authorization", "Bearer ${token()}")
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", length.toString())
                .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val uploadUrl = http.newCall(initiate).execute().use { response ->
                if (!response.isSuccessful) error("Không thể khởi tạo tải lên (HTTP ${response.code}).")
                response.header("Location") ?: error("Google Drive không trả đường dẫn tải lên.")
            }
            val streamBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = length
                override fun writeTo(sink: BufferedSink) {
                    val input = appContext.contentResolver.openInputStream(uri)
                        ?: error("Không mở được tệp nguồn.")
                    input.source().use { source -> sink.writeAll(source) }
                }
            }
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer ${token()}")
                .put(streamBody)
                .build()
            http.newCall(uploadRequest).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Tải lên thất bại HTTP ${response.code}: ${text.take(250)}")
                val item = JSONObject(text)
                DriveFileItem(
                    item.getString("id"),
                    item.optString("name"),
                    item.optString("mimeType"),
                    item.optString("size").toLongOrNull() ?: length,
                    item.optString("modifiedTime")
                )
            }
        }

    suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("trashed", true)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${Uri.encode(fileId)}")
            .header("Authorization", "Bearer ${token()}")
            .patch(body).build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) error("Không thể chuyển tệp vào thùng rác (HTTP ${response.code}).")
        }
    }

    suspend fun download(fileId: String, displayName: String): File = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${Uri.encode(fileId)}?alt=media")
            .header("Authorization", "Bearer ${token()}").get().build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("Tải xuống thất bại HTTP ${response.code}.")
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
            val output = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, safeName)
            output.outputStream().use { out -> response.body?.byteStream()?.copyTo(out) }
            output
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SAVED_AT = "saved_at"
        private const val TOKEN_LIFETIME_MS = 50 * 60 * 1000L
    }
}
