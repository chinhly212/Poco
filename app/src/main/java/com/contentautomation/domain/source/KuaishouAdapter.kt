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

/**
 * Kuaishou creator-feed scanning is performed by the user's AI Video Server.
 * The server uses the non-commercial MediaCrawler-derived signing flow.
 */
class KuaishouAdapter : YtDlpProfileAdapter() {
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
                    identifier = profile.optString("user_id").takeIf { it.isNotBlank() && it != "null" } ?: identifier,
                    description = profile.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    avatarUrl = profile.optString("avatar_url").takeIf { it.startsWith("http") },
                    videoCount = root.optInt("video_count").takeIf { it >= 0 },
                    followerCount = profile.optLong("follower_count").takeIf { it > 0L }
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
            .connectTimeout(20, TimeUnit.SECONDS)
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

        val profile = profileUrl(accountIdentifier)
        val payload = JSONObject()
            .put("url", profile)
            .put("cookie", cookieHeader)
            .put("max_pages", 10)
        val request = Request.Builder()
            .url("$SERVER_BASE/kuaishou/profile/videos")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        try {
            serverClient.newCall(request).execute().use { response ->
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
                val entries = root.optJSONArray("videos")
                    ?: throw SourceUnavailableException("Máy chủ không trả danh sách video.")
                buildList {
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
                        add(
                            SourceVideoItem(
                                id = id,
                                url = url,
                                title = item.optString("title").takeIf { it.isNotBlank() },
                                description = item.optString("description").takeIf { it.isNotBlank() },
                                publishedAt = publishedAt
                            )
                        )
                    }
                }.distinctBy { it.id }
            }
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
    }
}
