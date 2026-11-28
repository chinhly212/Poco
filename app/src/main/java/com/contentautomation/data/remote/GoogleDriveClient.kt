package com.contentautomation.data.remote

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import org.json.JSONArray
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.DriveScopes
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
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .remove(KEY_BACKUP_FOLDER_ID)
            .apply()
    }

    fun clearAccessToken() {
        securePrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_SAVED_AT)
            .remove(KEY_BACKUP_FOLDER_ID)
            .apply()
    }

    fun hasSavedToken(): Boolean =
        !securePrefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()

    fun isAutoBackupEnabled(): Boolean =
        securePrefs.getBoolean(KEY_AUTO_BACKUP, true)

    fun setAutoBackupEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    private suspend fun token(): String {
        val saved = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        val savedAt = securePrefs.getLong(KEY_SAVED_AT, 0L)
        if (!saved.isNullOrBlank() && System.currentTimeMillis() - savedAt < TOKEN_LIFETIME_MS) {
            return saved
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DriveScopes.DRIVE_FILE),
                    Scope("openid"),
                    Scope("email"),
                    Scope("profile")
                )
            )
            .build()
        val authorization = Tasks.await(
            Identity.getAuthorizationClient(appContext).authorize(request),
            30,
            TimeUnit.SECONDS
        )
        if (authorization.hasResolution()) {
            error("Google Drive cần xác nhận lại quyền. Hãy mở MuChi → Cài đặt → Kết nối Google Drive.")
        }
        val refreshed = authorization.accessToken
            ?: error("Google không cấp access token mới. Hãy kết nối lại Google Drive.")
        saveAccessToken(refreshed)
        return refreshed
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

    suspend fun upload(
        uri: Uri,
        displayName: String,
        mimeType: String,
        backupKey: String? = null
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val length = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: -1L
        if (length <= 0L) error("Không xác định được dung lượng tệp để tải lên.")

        if (!backupKey.isNullOrBlank()) {
            val escapedKey = backupKey.replace("'", "\\'")
            val query = "trashed = false and appProperties has { key='muchi_backup_key' and value='$escapedKey' }"
            val existing = getJson(
                "https://www.googleapis.com/drive/v3/files?q=${Uri.encode(query)}&pageSize=1&fields=files(id,name,mimeType,size,modifiedTime)"
            ).optJSONArray("files")
            if (existing != null && existing.length() > 0) {
                val item = existing.getJSONObject(0)
                return@withContext DriveFileItem(
                    item.getString("id"),
                    item.optString("name"),
                    item.optString("mimeType"),
                    item.optString("size").toLongOrNull() ?: length,
                    item.optString("modifiedTime")
                )
            }
        }

        val metadata = JSONObject()
            .put("name", displayName)
            .put("mimeType", mimeType)
        if (!backupKey.isNullOrBlank()) {
            metadata.put("appProperties", JSONObject().put("muchi_backup_key", backupKey))
            metadata.put("parents", JSONArray().put(ensureBackupFolderId()))
        }

        val bearer = token()
        val initiate = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,mimeType,size,modifiedTime")
            .header("Authorization", "Bearer $bearer")
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
            .header("Authorization", "Bearer $bearer")
            .put(streamBody)
            .build()
        http.newCall(uploadRequest).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Tải lên thất bại HTTP ${response.code}: ${responseText.take(250)}")
            val item = JSONObject(responseText)
            DriveFileItem(
                item.getString("id"),
                item.optString("name"),
                item.optString("mimeType"),
                item.optString("size").toLongOrNull() ?: length,
                item.optString("modifiedTime")
            )
        }
    }

    private suspend fun ensureBackupFolderId(): String {
        securePrefs.getString(KEY_BACKUP_FOLDER_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val query = "name = 'MuChi Auto Backup' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val found = getJson(
            "https://www.googleapis.com/drive/v3/files?q=${Uri.encode(query)}&pageSize=5&fields=files(id)"
        ).optJSONArray("files")
        val existingId = found?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        if (existingId != null) {
            securePrefs.edit().putString(KEY_BACKUP_FOLDER_ID, existingId).apply()
            return existingId
        }

        val metadata = JSONObject()
            .put("name", "MuChi Auto Backup")
            .put("mimeType", "application/vnd.google-apps.folder")
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer ${token()}")
            .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val folderId = http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Không tạo được thư mục sao lưu Drive (HTTP ${response.code}).")
            JSONObject(body).optString("id").takeIf { it.isNotBlank() }
                ?: error("Google Drive không trả mã thư mục sao lưu.")
        }
        securePrefs.edit().putString(KEY_BACKUP_FOLDER_ID, folderId).apply()
        return folderId
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
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
        private const val KEY_BACKUP_FOLDER_ID = "backup_folder_id"
        private const val TOKEN_LIFETIME_MS = 45 * 60 * 1000L
    }
}
