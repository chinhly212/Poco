package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

/**
 * Kuaishou creator-feed scanning is performed by the user's AI Video Server.
 * The server uses the non-commercial MediaCrawler-derived signing flow.
 */
class KuaishouAdapter(cookieDirectory: java.io.File? = null) : YtDlpProfileAdapter(cookieDirectory) {
    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.kuaishou.com/profile/$identifier"

    override fun videoUrl(videoId: String): String =
        "https://www.kuaishou.com/short-video/$videoId"

    override suspend fun verifyProfile(
        identifier: String,
        cookieHeader: String
    ): SourceProfileInfo = withContext(Dispatchers.IO) {
        require(identifier.isNotBlank()) { "ID tài khoản Kuaishou không được để trống." }
        require(cookieHeader.isNotBlank()) { "Chưa có phiên đăng nhập Kuaishou. Hãy đăng nhập lại." }
        val payload = JSONObject()
            .put("url", profileUrl(identifier))
            .put("cookie", cookieHeader)
            .put("max_pages", 1)
        val request = Request.Builder()
            .url("$SERVER_BASE/kuaishou/profile/videos")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        try {
            serverClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (!response.isSuccessful) {
                    val detail = root?.optString("detail")?.takeIf { it.isNotBlank() }
                        ?: "Máy chủ Kuaishou trả HTTP ${response.code} khi xác minh hồ sơ."
                    throw SourceUnavailableException(detail)
                }
                val profile = root?.optJSONObject("profile")
                    ?: throw SourceUnavailableException("Máy chủ không trả dữ liệu hồ sơ Kuaishou.")
                val displayName = profile.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                    ?: throw SourceUnavailableException("Kuaishou không trả tên hồ sơ. Có thể ID sai, phiên đăng nhập hết hạn hoặc hồ sơ không có video công khai.")
                SourceProfileInfo(
                    displayName = displayName,
                    identifier = profile.optString("profile_id").takeIf { it.isNotBlank() && it != "null" } ?: identifier,
                    description = profile.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    avatarUrl = profile.optString("avatar_url").takeIf { it.startsWith("http") },
                    videoCount = profile.optInt("video_count", -1).takeIf { it >= 0 },
                    followerCount = profile.optLong("follower_count", -1L).takeIf { it >= 0L },
                    followingCount = profile.optLong("following_count", -1L).takeIf { it >= 0L }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SourceUnavailableException("Không xác minh được hồ sơ Kuaishou qua máy chủ: ${e.message ?: "lỗi kết nối"}", e)
        }
    }

    private val serverClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun getLatestVideos(
        accountIdentifier: String,
        cookieHeader: String?
    ): List<SourceVideoItem> = withContext(Dispatchers.IO) {
        require(accountIdentifier.isNotBlank()) { "URL hoặc ID tài khoản Kuaishou không được để trống." }
        require(!cookieHeader.isNullOrBlank()) {
            "Chưa có phiên đăng nhập Kuaishou. Hãy đăng nhập lại trong MuChi rồi lưu phiên."
        }

        try {
            val profile = profileUrl(accountIdentifier)
            val sinceTimestamp = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val collected = linkedMapOf<String, SourceVideoItem>()
            var cursor = ""

            // Each request asks the backend for exactly one Kuaishou page.
            repeat(MAX_SCAN_PAGES) {
                val payload = JSONObject()
                    .put("url", profile)
                    .put("cookie", cookieHeader)
                    .put("max_pages", 1)
                    .put("cursor", cursor)
                    .put("since_timestamp", sinceTimestamp)
                val request = Request.Builder()
                    .url("$SERVER_BASE/kuaishou/profile/videos")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "application/json")
                    .build()

                val page = serverClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    if (!response.isSuccessful) {
                        val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                            ?: "Máy chủ Kuaishou trả HTTP ${response.code}."
                        throw SourceUnavailableException(detail)
                    }
                    val root = json ?: throw SourceUnavailableException("Máy chủ trả dữ liệu Kuaishou không hợp lệ.")
                    if (root.optString("status") != "success") {
                        throw SourceUnavailableException("Máy chủ chưa xác nhận quét Kuaishou thành công.")
                    }
                    root
                }

                val entries = page.optJSONArray("videos")
                    ?: throw SourceUnavailableException("Máy chủ không trả danh sách video Kuaishou.")
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val url = item.optString("url").takeIf { it.startsWith("https://") }
                        ?: videoUrl(id)
                    val published = item.opt("published_at")
                    val publishedAt = when (published) {
                        is Number -> published.toLong().let { if (it in 1..9_999_999_999L) it * 1000L else it }
                        is String -> published.toLongOrNull()?.let { if (it in 1..9_999_999_999L) it * 1000L else it }
                        else -> null
                    }
                    collected.putIfAbsent(id, SourceVideoItem(
                        id = id,
                        url = url,
                        title = item.optString("title").takeIf { it.isNotBlank() },
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        publishedAt = publishedAt
                    ))
                }

                val nextCursor = page.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
                if (!page.optBoolean("has_more", false) || nextCursor == null || nextCursor == cursor) {
                    return@withContext collected.values.toList()
                }
                cursor = nextCursor
                kotlinx.coroutines.delay(PAGE_DELAY_MS)
            }
            collected.values.toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SourceUnavailableException(
                "Không kết nối được dịch vụ quét Kuaishou: ${e.message ?: "lỗi mạng"}",
                e
            )
        }
    }

    private companion object {
        const val SERVER_BASE = "https://ai-video-server-59tj.onrender.com"
        const val MAX_SCAN_PAGES = 20
        const val PAGE_DELAY_MS = 900L
    }
}
