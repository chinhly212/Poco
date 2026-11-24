package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

class DouyinAdapter(cookieDirectory: java.io.File? = null) : YtDlpProfileAdapter(cookieDirectory) {

    private val profileClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.douyin.com/user/$identifier"

    override fun videoUrl(videoId: String): String =
        "https://www.douyin.com/video/$videoId"

    override suspend fun getLatestVideos(
        accountIdentifier: String,
        cookieHeader: String?
    ): List<SourceVideoItem> = withContext(Dispatchers.IO) {
        if (!accountIdentifier.startsWith("MS4wLjAB")) {
            throw SourceUnavailableException("Mã hồ sơ Douyin chưa được xác minh. Hãy xóa kênh và thêm lại bằng link trang cá nhân.")
        }
        try {
            val payload = JSONObject()
                .put("sec_uid", accountIdentifier)
                .put("cookie", cookieHeader.orEmpty())
                .put("since_timestamp", LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .put("max_items", 100)
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://ai-video-server-59tj.onrender.com/douyin/profile/videos")
                .post(body)
                .header("Accept", "application/json")
                .build()
            profileClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                val result = runCatching { JSONObject(responseText) }.getOrNull()
                if (!response.isSuccessful || result?.optString("status") != "success") {
                    val serverDetail = result?.optString("detail")?.takeIf { it.isNotBlank() }
                    val responsePreview = responseText
                        .replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(500)
                    val detail = serverDetail
                        ?: responsePreview.takeIf { it.isNotBlank() }?.let {
                            "Máy chủ quét Douyin trả HTTP ${response.code}: $it"
                        }
                        ?: "Máy chủ quét Douyin trả HTTP ${response.code} nhưng không có nội dung phản hồi."
                    throw SourceUnavailableException(detail)
                }
                val entries = result.optJSONArray("videos") ?: return@withContext emptyList()
                buildList {
                    for (index in 0 until entries.length()) {
                        val item = entries.optJSONObject(index) ?: continue
                        val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                        val url = item.optString("url").takeIf { it.startsWith("http") }
                            ?: "https://www.douyin.com/video/$id"
                        val timestamp = item.optLong("published_at", 0L).takeIf { it > 0L }
                        add(SourceVideoItem(
                            id = id,
                            url = url,
                            title = item.optString("title").takeIf { it.isNotBlank() && it != "null" },
                            description = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                            publishedAt = timestamp
                        ))
                    }
                }.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SourceUnavailableException("Không thể quét danh sách video Douyin qua máy chủ: ${e.message ?: "lỗi kết nối"}", e)
        }
    }

    override suspend fun verifyProfile(
        identifier: String,
        cookieHeader: String
    ): SourceProfileInfo = withContext(Dispatchers.IO) {
        require(identifier.isNotBlank()) { "Hãy dán link trang cá nhân Douyin." }
        require(cookieHeader.isNotBlank()) { "Chưa đăng nhập Douyin. Hãy đăng nhập rồi lưu phiên trước." }

        try {
            val request = Request.Builder()
                .url(profileUrl(identifier))
                .header("Cookie", cookieHeader)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.douyin.com/")
                .build()

            val (resolvedUrl, html) = profileClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceUnavailableException("Douyin trả về HTTP ${response.code} khi mở trang cá nhân.")
                }
                response.request.url.toString() to (response.body?.string().orEmpty())
            }
            if (html.isBlank()) throw SourceUnavailableException("Douyin không trả nội dung hồ sơ.")

            val finalUri = android.net.Uri.parse(resolvedUrl)
            val host = finalUri.host.orEmpty().lowercase()
            if (!(host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com"))) {
                throw SourceUnavailableException("Liên kết không dẫn tới trang cá nhân Douyin.")
            }
            if (!finalUri.pathSegments.contains("user")) {
                throw SourceUnavailableException("Liên kết bạn dán không phải trang cá nhân Douyin. Hãy sao chép link hồ sơ của người dùng.")
            }

            fun stringField(vararg keys: String): String? {
                for (key in keys) {
                    val pattern = Regex(
                        """["']${Regex.escape(key)}["']\s*:\s*["']([^"']{1,1000})["']""",
                        RegexOption.IGNORE_CASE
                    )
                    val value = pattern.find(html)?.groupValues?.getOrNull(1)
                        ?.replace("\\u002F", "/")
                        ?.replace("\\u0026", "&")
                        ?.trim()
                    if (!value.isNullOrBlank() && value != "null") return value
                }
                return null
            }

            fun numberField(vararg keys: String): Long? {
                for (key in keys) {
                    val pattern = Regex(
                        """["']${Regex.escape(key)}["']\s*:\s*["']?(\d+)""",
                        RegexOption.IGNORE_CASE
                    )
                    val value = pattern.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    if (value != null) return value
                }
                return null
            }

            val pathId = finalUri.pathSegments
                .let { segments -> segments.indexOf("user").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) } }
            val secUid = stringField("sec_uid", "secUid", "sec_user_id")
                ?: Regex("""/user/(MS4wLjAB[A-Za-z0-9_-]+)""").find(resolvedUrl)?.groupValues?.getOrNull(1)
                ?: pathId?.takeIf { it.startsWith("MS4wLjAB") }

            if (secUid.isNullOrBlank() || !secUid.startsWith("MS4wLjAB")) {
                throw SourceUnavailableException(
                    "Chưa lấy được mã hồ sơ Douyin (sec_uid). Hãy sao chép liên kết trang cá nhân, không dùng ID hiển thị hoặc liên kết video."
                )
            }

            val nickname = stringField("nickname", "user_name", "display_name")
                ?: throw SourceUnavailableException("Đã mở được hồ sơ nhưng Douyin không trả tên tài khoản. Không thể xác nhận thành công.")
            val avatar = stringField("avatar_larger", "avatar_url", "avatar_thumb", "avatar")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val description = stringField("signature", "desc", "description")

            SourceProfileInfo(
                displayName = nickname,
                identifier = secUid,
                description = description,
                avatarUrl = avatar,
                videoCount = numberField("aweme_count", "video_count")?.toInt(),
                followerCount = numberField("follower_count", "followerCount", "mplatform_followers_count"),
                followingCount = numberField("following_count", "followingCount")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SourceUnavailableException("Không xác minh được hồ sơ Douyin: ${e.message ?: "lỗi kết nối"}", e)
        }
    }
}
