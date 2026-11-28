package com.contentautomation.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AiVideoDownloadResult(
    val provider: String,
    val title: String?,
    val bytesWritten: Long
)

data class AiServerHealth(
    val version: String,
    val aiReady: Boolean,
    val configuredProviders: List<String>
)

data class AiChatTurn(val role: String, val content: String)

data class AiVideoChatResult(
    val type: String,
    val message: String,
    val videoUrl: String? = null,
    val provider: String? = null,
    val title: String? = null,
    val providerErrors: List<String> = emptyList()
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    suspend fun getHealth(): AiServerHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/health").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Health HTTP ${response.code}: ${body.take(200)}")
            val json = JSONObject(body)
            if (json.optString("status") != "healthy") throw IOException("Máy chủ chưa sẵn sàng.")
            val providersJson = json.optJSONArray("ai_providers")
            val providers = buildList {
                if (providersJson != null) {
                    for (index in 0 until providersJson.length()) add(providersJson.optString(index))
                } else {
                    if (json.optBoolean("gemini_configured")) add("Gemini")
                    if (json.optBoolean("openrouter_configured")) add("OpenRouter")
                    if (json.optBoolean("deepseek_configured")) add("DeepSeek")
                }
            }
            AiServerHealth(
                version = json.optString("version", "unknown"),
                aiReady = json.optBoolean("ai_ready", providers.isNotEmpty()),
                configuredProviders = providers
            )
        }
    }

    suspend fun checkHealth(): String {
        val health = getHealth()
        return if (health.aiReady) {
            "Máy chủ hoạt động · AI: ${health.configuredProviders.joinToString()}"
        } else {
            "Máy chủ hoạt động nhưng chưa cấu hình API mô hình AI."
        }
    }

    suspend fun validateProvider(provider: String, apiKey: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key không được để trống." }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/validate")
            .post(JSONObject().put("provider", provider.lowercase()).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("X-Provider-Api-Key", apiKey)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (!response.isSuccessful || json?.optBoolean("valid") != true) {
                throw IOException((json?.optString("message")?.takeIf { it.isNotBlank() } ?: json?.optString("detail")?.takeIf { it.isNotBlank() })
                    ?: "Kiểm tra API thất bại: HTTP ${response.code}")
            }
            json.optString("message", "Kết nối thành công")
        }
    }

    suspend fun sendChat(
        message: String,
        apiKeys: Map<String, String> = emptyMap(),
        history: List<AiChatTurn> = emptyList()
    ): AiVideoChatResult = withContext(Dispatchers.IO) {
        val historyJson = JSONArray()
        history.takeLast(12).forEach { turn ->
            if (turn.role in setOf("user", "assistant") && turn.content.isNotBlank()) {
                historyJson.put(
                    JSONObject()
                        .put("role", turn.role)
                        .put("content", turn.content.take(2000))
                )
            }
        }
        val json = JSONObject()
            .put("message", message)
            .put("history", historyJson)
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        apiKeys["gemini"]?.takeIf { it.isNotBlank() }?.let { builder.header("X-Gemini-Api-Key", it) }
        apiKeys["openrouter"]?.takeIf { it.isNotBlank() }?.let { builder.header("X-OpenRouter-Api-Key", it) }
        apiKeys["deepseek"]?.takeIf { it.isNotBlank() }?.let { builder.header("X-DeepSeek-Api-Key", it) }
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("AI HTTP ${response.code}: ${body.take(300)}")
            val payload = JSONObject(body)
            val data = payload.optJSONObject("data")
            AiVideoChatResult(
                type = payload.optString("type", "text"),
                message = payload.optString("message", "Máy chủ không trả lời."),
                videoUrl = data?.optString("video_url")?.takeIf { it.isNotBlank() },
                provider = data?.optString("provider")?.takeIf { it.isNotBlank() },
                title = data?.optString("title")?.takeIf { it.isNotBlank() },
                providerErrors = (payload.optJSONArray("errors")
                    ?: payload.optJSONArray("provider_errors"))?.let { errors ->
                    buildList {
                        for (index in 0 until errors.length()) {
                            errors.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
            )
        }
    }

    suspend fun downloadResolvedVideoToGallery(
        videoUrl: String,
        saver: MediaStoreSaver,
        displayName: String,
        provider: String?,
        title: String?,
        sourceUrl: String
    ): AiVideoDownloadResult = withContext(Dispatchers.IO) {
        val requestJson = JSONObject()
            .put("video_url", videoUrl)
            .put("source_url", sourceUrl)
            .put("provider", provider.orEmpty())
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/download/proxy")
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "video/*,application/octet-stream")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyText = response.body?.string().orEmpty()
                val detail = runCatching { JSONObject(bodyText).optString("detail") }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                    ?: bodyText.take(300).ifBlank { "HTTP ${response.code}" }
                throw IOException("Không thể nhận video từ nguồn tải: ${detail.take(240)}")
            }
            val body = response.body ?: throw IOException("Nguồn video trả dữ liệu rỗng.")
            val type = body.contentType()?.toString()?.lowercase().orEmpty()
            if (type.contains("text/html") || type.contains("application/json")) {
                throw IOException("Nguồn tải không trả dữ liệu video.")
            }
            val expectedBytes = body.contentLength().takeIf { it > 0L }
            saver.saveVideoFromStream(
                body.byteStream(),
                displayName,
                expectedBytes
            )
            val savedBytes = expectedBytes ?: -1L
            AiVideoDownloadResult(
                provider = provider ?: "MuChi",
                title = title,
                bytesWritten = savedBytes
            )
        }
    }


    suspend fun downloadVideo(
        sourceUrl: String,
        destination: File,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): AiVideoDownloadResult = withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            val resolveRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/download")
                .post(JSONObject().put("url", sourceUrl).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .build()

            try {
                val payload = client.newCall(resolveRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errorPayload = runCatching { JSONObject(body) }.getOrNull()
                        val message = errorPayload?.optString("message")
                            ?.takeIf { it.isNotBlank() }
                            ?: errorPayload?.optString("detail")?.takeIf { it.isNotBlank() }
                        val providerErrors = (errorPayload?.optJSONArray("provider_errors")
                            ?: errorPayload?.optJSONArray("errors"))?.let { errors ->
                            buildList {
                                for (index in 0 until errors.length()) {
                                    errors.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                                }
                            }
                        }.orEmpty()
                        val detail = buildList {
                            message?.let(::add)
                            addAll(providerErrors)
                        }.joinToString("\\n").ifBlank { body.take(500) }
                        throw IOException("Máy chủ không tìm được video: ${detail}")
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

                // Never fetch the CDN URL directly from Android. The server proxy
                // handles CDN 409 responses and falls back to its server-side yt-dlp.
                val proxyPayload = JSONObject()
                    .put("video_url", videoUrl)
                    .put("source_url", sourceUrl)
                    .put("provider", payload.optString("provider", ""))
                val proxyRequest = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/download/proxy")
                    .post(proxyPayload.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "video/*,application/octet-stream")
                    .build()

                val bytes = client.newCall(proxyRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyText = response.body?.string().orEmpty()
                        val detail = runCatching { JSONObject(bodyText).optString("detail") }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                            ?: bodyText.take(400).ifBlank { "HTTP ${response.code}" }
                        throw IOException("Không thể nhận video qua máy chủ: ${detail}")
                    }
                    val body = response.body ?: throw IOException("Máy chủ trả dữ liệu video rỗng.")
                    val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
                    if (contentType.contains("text/html") || contentType.contains("application/json")) {
                        throw IOException("Máy chủ không trả dữ liệu video (${contentType}).")
                    }
                    val expectedBytes = body.contentLength().takeIf { it > 0L }
                    var copied = 0L
                    var lastReported = 0L
                    destination.outputStream().buffered().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                if (copied - lastReported >= 256 * 1024 || (expectedBytes != null && copied >= expectedBytes)) {
                                    output.flush()
                                    onProgress(copied, expectedBytes)
                                    lastReported = copied
                                }
                            }
                            output.flush()
                        }
                    }
                    if (copied > lastReported) onProgress(copied, expectedBytes)
                    if (copied <= 0L) throw IOException("Video tải về bị rỗng.")
                    if (expectedBytes != null && copied != expectedBytes) {
                        throw IOException("Video chưa tải đủ dữ liệu: ${copied}/${expectedBytes} byte.")
                    }
                    copied
                }

                if (!destination.exists() || destination.length() != bytes || bytes <= 0L) {
                    throw IOException("Không xác minh được file video sau khi tải.")
                }

                AiVideoDownloadResult(
                    provider = payload.optString("provider", "MuChi"),
                    title = payload.optString("title").takeIf { it.isNotBlank() },
                    bytesWritten = bytes
                )
            } catch (error: Exception) {
                destination.delete()
                if (error is IOException) throw error
                throw IOException("Không thể tải video qua máy chủ: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://ai-video-server-59tj.onrender.com"
    }
}
