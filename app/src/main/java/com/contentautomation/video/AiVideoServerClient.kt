package com.contentautomation.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AiVideoDownloadResult(
    val provider: String,
    val title: String?,
    val bytesWritten: Long
)

data class AiVideoChatResult(
    val type: String,
    val message: String,
    val videoUrl: String? = null,
    val provider: String? = null
)

/**
 * Calls the user's AI Video Server. Provider failover (GenDownload and its
 * fallbacks) is performed by the server; this client only streams the
 * resulting temporary video URL to the device.
 */
class AiVideoServerClient(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkHealth(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/health").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Health HTTP ${response.code}: ${body.take(200)}")
            val json = JSONObject(body)
            if (json.optString("status") != "healthy") throw IOException("Máy chủ chưa sẵn sàng.")
            "Đang hoạt động · phiên bản ${json.optString("version", "unknown")}"
        }
    }

    suspend fun sendChat(message: String): AiVideoChatResult = withContext(Dispatchers.IO) {
        val json = JSONObject().put("message", message)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("AI HTTP ${response.code}: ${body.take(300)}")
            val payload = JSONObject(body)
            val data = payload.optJSONObject("data")
            AiVideoChatResult(
                type = payload.optString("type", "text"),
                message = payload.optString("message", "Máy chủ không trả lời."),
                videoUrl = data?.optString("video_url")?.takeIf { it.isNotBlank() },
                provider = data?.optString("provider")?.takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun downloadVideo(sourceUrl: String, destination: File): AiVideoDownloadResult =
        withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val requestJson = JSONObject().put("url", sourceUrl)
            val resolveRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/download")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            val payload = client.newCall(resolveRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    throw IOException("AI Video Server HTTP ${response.code}: ${detail ?: body.take(300)}")
                }
                JSONObject(body)
            }

            if (payload.optString("status") != "success") {
                throw IOException(payload.optString("message", "Máy chủ không tìm được video."))
            }

            val videoUrl = payload.optString("video_url")
            if (!videoUrl.startsWith("https://") && !videoUrl.startsWith("http://")) {
                throw IOException("Máy chủ không trả về đường dẫn video hợp lệ.")
            }

            val videoRequest = Request.Builder()
                .url(videoUrl)
                .get()
                .header("Accept", "video/*,application/octet-stream;q=0.9,*/*;q=0.8")
                .build()

            val bytes = client.newCall(videoRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Không tải được file video: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Máy chủ video trả nội dung rỗng.")
                val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    throw IOException("Đường dẫn trả về không phải file video ($contentType).")
                }
                destination.outputStream().buffered().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }

            if (bytes <= 0L || !destination.exists() || destination.length() <= 0L) {
                destination.delete()
                throw IOException("File video tải về rỗng.")
            }

            AiVideoDownloadResult(
                provider = payload.optString("provider", "AI Video Server"),
                title = payload.optString("title").takeIf { it.isNotBlank() },
                bytesWritten = bytes
            )
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://ai-video-server-59tj.onrender.com"
    }
}
