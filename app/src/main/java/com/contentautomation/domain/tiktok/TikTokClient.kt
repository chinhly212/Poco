
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
    val status: String,
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

    private fun parseSuccessfulResponse(
        response: Response,
        operation: String
    ): JSONObject {
        val body = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException(
                "$operation HTTP ${response.code}: $body"
            )
        }

        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw RuntimeException(
                "$operation: Phản hồi JSON không hợp lệ",
                e
            )
        }

        val error = json.optJSONObject("error")

        if (error != null) {
            val code = error.optString("code")

            if (code.isNotBlank() && code != "ok") {
                throw RuntimeException(
                    "$operation API error [$code]: " +
                        error.optString("message")
                )
            }
        }

        return json
    }

    suspend fun refreshIfNeeded(): String =
        withContext(Dispatchers.IO) {
            val token = prefs.getAccessToken()
                ?: throw IllegalStateException(
                    "TikTok chưa đăng nhập"
                )

            val expiresAt = prefs.getTokenExpiresAt()

            if (expiresAt - System.currentTimeMillis() > 60_000L) {
                return@withContext token
            }

            val refresh = prefs.getRefreshToken()
                ?: throw IllegalStateException(
                    "Không có refresh token"
                )

            val body = FormBody.Builder()
                .add("client_key", clientKeyProvider())
                .add("client_secret", clientSecretProvider())
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build()

            val req = Request.Builder()
                .url("https://open.tiktokapis.com/v2/oauth/token/")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                val json = parseSuccessfulResponse(
                    resp,
                    "Refresh token"
                )

                val newToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 0L)

                if (
                    newToken.isBlank() ||
                    newRefreshToken.isBlank() ||
                    expiresIn <= 0L
                ) {
                    throw RuntimeException(
                        "TikTok trả về refresh token không hợp lệ"
                    )
                }

                prefs.setAccessToken(newToken)
                prefs.setRefreshToken(newRefreshToken)
                prefs.setTokenExpiresAt(
                    System.currentTimeMillis() +
                        expiresIn * 1000L
                )

                json.optString("open_id")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { prefs.setOpenId(it) }

                newToken
            }
        }

    suspend fun getCreatorInfo(): CreatorInfo =
        withContext(Dispatchers.IO) {
            val token = refreshIfNeeded()

            val req = Request.Builder()
                .url(
                    "https://open.tiktokapis.com/v2/" +
                        "post/publish/creator_info/query/"
                )
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(JSON))
                .build()

            http.newCall(req).execute().use { resp ->
                val json = parseSuccessfulResponse(
                    resp,
                    "Creator info"
                )

                val data = json.optJSONObject("data")
                    ?: throw RuntimeException(
                        "TikTok không trả về creator info"
                    )

                CreatorInfo(
                    username = data.optString("creator_username"),
                    maxVideoPostDurationSec =
                        data.optInt(
                            "max_video_post_duration_sec",
                            600
                        ),
                    privacyLevelOptions =
                        data.optJSONArray(
                            "privacy_level_options"
                        )?.let { arr ->
                            (0 until arr.length()).map {
                                arr.getString(it)
                            }
                        } ?: listOf("SELF_ONLY")
                )
            }
        }

    suspend fun initializePost(
        title: String,
        privacyLevel: String,
        videoSize: Long
    ): InitResponse = withContext(Dispatchers.IO) {
        require(videoSize > 0L) {
            "Video không hợp lệ hoặc rỗng"
        }

        require(privacyLevel.isNotBlank()) {
            "Chưa chọn quyền riêng tư"
        }

        val (chunkSize, totalChunks) = computeChunks(videoSize)
        val token = refreshIfNeeded()

        val payload = JSONObject().apply {
            put(
                "post_info",
                JSONObject().apply {
                    put("title", title.take(2200))
                    put("privacy_level", privacyLevel)
                    put("disable_duet", false)
                    put("disable_comment", false)
                    put("disable_stitch", false)
                    put("video_cover_timestamp_ms", 1000)
                }
            )

            put(
                "source_info",
                JSONObject().apply {
                    put("source", "FILE_UPLOAD")
                    put("video_size", videoSize)
                    put("chunk_size", chunkSize)
                    put("total_chunk_count", totalChunks)
                }
            )
        }

        val req = Request.Builder()
            .url(
                "https://open.tiktokapis.com/v2/" +
                    "post/publish/video/init/"
            )
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        http.newCall(req).execute().use { resp ->
            val json = parseSuccessfulResponse(
                resp,
                "Initialize post"
            )

            val data = json.optJSONObject("data")
                ?: throw RuntimeException(
                    "TikTok không trả về dữ liệu khởi tạo"
                )

            val publishId = data.optString("publish_id")
            val uploadUrl = data.optString("upload_url")

            if (publishId.isBlank() || uploadUrl.isBlank()) {
                throw RuntimeException(
                    "TikTok trả về publish_id hoặc upload_url rỗng"
                )
            }

            InitResponse(publishId, uploadUrl)
        }
    }

    private fun computeChunks(size: Long): Pair<Long, Int> {
        val mb = 1024L * 1024L

        return if (size <= 64L * mb) {
            size to 1
        } else {
            val chunkSize = 10L * mb
            chunkSize to (
                (size + chunkSize - 1) / chunkSize
            ).toInt()
        }
    }

    suspend fun uploadFile(
        uploadUrl: String,
        file: File,
        publishId: String
    ): Boolean = withContext(Dispatchers.IO) {
        require(file.exists() && file.isFile) {
            "Không tìm thấy file video"
        }

        val size = file.length()

        require(size > 0L) {
            "File video rỗng"
        }

        require(uploadUrl.isNotBlank()) {
            "Upload URL không hợp lệ"
        }

        require(publishId.isNotBlank()) {
            "Publish ID không hợp lệ"
        }

        val (chunkSize, totalChunks) = computeChunks(size)
        val raf = java.io.RandomAccessFile(file, "r")

        try {
            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = (
                    (i + 1) * chunkSize - 1
                ).coerceAtMost(size - 1)

                val length = (end - start + 1).toInt()
                val buffer = ByteArray(length)

                raf.seek(start)
                raf.readFully(buffer)

                val body = buffer.toRequestBody(
                    "video/mp4".toMediaType()
                )

                val req = Request.Builder()
                    .url(uploadUrl)
                    .put(body)
                    .header("Content-Type", "video/mp4")
                    .header("Content-Length", length.toString())
                    .header(
                        "Content-Range",
                        "bytes $start-$end/$size"
                    )
                    .build()

                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw RuntimeException(
                            "Upload chunk $i/$totalChunks " +
                                "HTTP ${resp.code}: " +
                                resp.body?.string().orEmpty()
                        )
                    }
                }
            }

            true
        } finally {
            raf.close()
        }
    }

    suspend fun fetchStatus(
        publishId: String
    ): PublishStatus = withContext(Dispatchers.IO) {
        require(publishId.isNotBlank()) {
            "Publish ID không hợp lệ"
        }

        val token = refreshIfNeeded()

        val payload = JSONObject()
            .put("publish_id", publishId)
            .toString()

        val req = Request.Builder()
            .url(
                "https://open.tiktokapis.com/v2/" +
                    "post/publish/status/fetch/"
            )
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody(JSON))
            .build()

        http.newCall(req).execute().use { resp ->
            val json = parseSuccessfulResponse(
                resp,
                "Fetch publish status"
            )

            val data = json.optJSONObject("data")
                ?: throw RuntimeException(
                    "TikTok trả về dữ liệu trạng thái không hợp lệ"
                )

            val status = data.optString("status")

            if (status.isBlank()) {
                throw RuntimeException(
                    "TikTok không trả về trạng thái bài đăng"
                )
            }

            PublishStatus(
                status = status,
                failReason = data.optString("fail_reason")
                    .takeIf {
                        it.isNotBlank() && it != "null"
                    },
                postId = data.optJSONArray(
                    "publicaly_available_post_id"
                )?.let { arr ->
                    if (arr.length() > 0) {
                        arr.optString(0)
                            .takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
            )
        }
    }
}
