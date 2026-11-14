package com.contentautomation.domain.tiktok

import com.contentautomation.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class CreatorInfo(
    val username: String,
    val maxVideoPostDurationSec: Int,
    val privacyLevelOptions: List<String>
)

data class InitResponse(
    val publishId: String,
    val uploadUrl: String
)

data class PublishStatus(
    val status: String,           // PROCESSING_UPLOAD, PUBLISH_COMPLETE, FAILED
    val failReason: String?,
    val postId: String?
)

class TikTokClient(
    private val prefs: AppPreferences,
    private val clientKeyProvider: () -> String,
    private val clientSecretProvider: () -> String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    fun getAccessToken(): String? = prefs.getAccessToken()

    suspend fun refreshIfNeeded(): String = withContext(Dispatchers.IO) {
        val token = prefs.getAccessToken() ?: throw IllegalStateException("TikTok chưa đăng nhập")
        val expiresAt = prefs.getTokenExpiresAt()
        if (expiresAt - System.currentTimeMillis() > 60_000L) return@withContext token

        val refresh = prefs.getRefreshToken() ?: throw IllegalStateException("Không có refresh token")
        val body = FormBody.Builder()
            .add("client_key", clientKeyProvider())
            .add("client_secret", clientSecretProvider())
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh).build()
        val req = Request.Builder()
            .url("https://open.tiktokapis.com/v2/oauth/token/")
            .post(body).build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body!!.string())
            if (!resp.isSuccessful || json.has("error")) {
                throw RuntimeException("Refresh failed: $json")
            }
            val newToken = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 86400)
            prefs.setAccessToken(newToken)
            prefs.setTokenExpiresAt(System.currentTimeMillis() + expiresIn * 1000)
            newToken
        }
    }

    suspend fun getCreatorInfo(): CreatorInfo = withContext(Dispatchers.IO) {
        val token = refreshIfNeeded()
        val req = Request.Builder()
            .url("https://open.tiktokapis.com/v2/post/publish/creator_info/query/")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(JSON)).build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body!!.string())
            if (!resp.isSuccessful) throw RuntimeException("Creator info: $json")
            val data = json.getJSONObject("data")
            CreatorInfo(
                username = data.optString("creator_username"),
                maxVideoPostDurationSec = data.optInt("max_video_post_duration_sec", 600),
                privacyLevelOptions = data.optJSONArray("privacy_level_options")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: listOf("SELF_ONLY")
            )
        }
    }

    suspend fun initializePost(
        title: String, privacyLevel: String, videoSize: Long
    ): InitResponse = withContext(Dispatchers.IO) {
        val (chunkSize, totalChunks) = computeChunks(videoSize)
        val token = refreshIfNeeded()
        val payload = JSONObject().apply {
            put("post_info", JSONObject().apply {
                put("title", title.take(2200))
                put("privacy_level", privacyLevel)
                put("disable_duet", false)
                put("disable_comment", false)
                put("disable_stitch", false)
                put("video_cover_timestamp_ms", 1000)
            })
            put("source_info", JSONObject().apply {
                put("source", "FILE_UPLOAD")
                put("video_size", videoSize)
                put("chunk_size", chunkSize)
                put("total_chunk_count", totalChunks)
            })
        }
        val req = Request.Builder()
            .url("https://open.tiktokapis.com/v2/post/publish/video/init/")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body!!.string())
            if (!resp.isSuccessful) throw RuntimeException("Init post: $json")
            val data = json.getJSONObject("data")
            InitResponse(
                publishId = data.getString("publish_id"),
                uploadUrl = data.getString("upload_url")
            )
        }
    }

    /**
     * TikTok chunk rules:
     * - Nếu size ≤ 64MB: 1 chunk = toàn bộ file
     * - Ngược lại: chia 10MB/chunk (chunk cuối được nhỏ hơn)
     */
    private fun computeChunks(size: Long): Pair<Long, Int> {
        val MB = 1024L * 1024L
        return if (size <= 64 * MB) size to 1
        else (10 * MB) to ((size + 10 * MB - 1) / (10 * MB)).toInt()
    }

    suspend fun uploadFile(uploadUrl: String, file: File, publishId: String): Boolean =
        withContext(Dispatchers.IO) {
            val size = file.length()
            val (chunkSize, totalChunks) = computeChunks(size)
            val raf = java.io.RandomAccessFile(file, "r")
            try {
                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = ((i + 1) * chunkSize - 1).coerceAtMost(size - 1)
                    val length = (end - start + 1).toInt()
                    val buffer = ByteArray(length)
                    raf.seek(start)
                    raf.readFully(buffer)
                    val body = buffer.toRequestBody("video/mp4".toMediaType())
                    val req = Request.Builder()
                        .url(uploadUrl)
                        .put(body)
                        .header("Content-Type", "video/mp4")
                        .header("Content-Length", length.toString())
                        .header("Content-Range", "bytes $start-$end/$size")
                        .build()
                    http.newCall(req).execute().use { resp ->
                        if (resp.code !in 200..299) {
                            throw RuntimeException("Chunk $i/$totalChunks HTTP ${resp.code}: ${resp.body?.string()}")
                        }
                    }
                }
                true
            } finally {
                raf.close()
            }
        }

    suspend fun fetchStatus(publishId: String): PublishStatus = withContext(Dispatchers.IO) {
        val token = refreshIfNeeded()
        val payload = JSONObject().put("publish_id", publishId).toString()
        val req = Request.Builder()
            .url("https://open.tiktokapis.com/v2/post/publish/status/fetch/")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(JSON)).build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body!!.string())
            val data = json.optJSONObject("data") ?: JSONObject()
            PublishStatus(
                status = data.optString("status", "PROCESSING_UPLOAD"),
                failReason = data.optString("fail_reason").takeIf { it.isNotBlank() && it != "null" },
                postId = data.optJSONArray("publicaly_available_post_id")?.let { arr ->
                    if (arr.length() > 0) arr.getString(0) else null
                }
            )
        }
    }
}
