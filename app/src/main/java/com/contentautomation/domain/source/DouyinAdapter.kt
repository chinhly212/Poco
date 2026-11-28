package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Reads a Douyin user's published videos through the mobile app's own HTTP client.
 * yt-dlp is intentionally not used for profile URLs: a profile page is not a media URL.
 * Video downloads still use yt-dlp later, once an aweme_id has been discovered.
 */
class DouyinAdapter(cookieDirectory: File? = null) : YtDlpProfileAdapter(cookieDirectory) {

    private val cookieDirectoryPath = cookieDirectory ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")

    private companion object {
        const val MAX_VIDEOS = 600
        const val SCAN_API_URL = "https://ai-video-server-59tj.onrender.com/douyin/profile/videos"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val scanClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(40, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }

    private val profileClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.douyin.com/user/$identifier"

    override fun videoUrl(videoId: String): String = "https://www.douyin.com/video/$videoId"

    override suspend fun getLatestVideos(
        accountIdentifier: String,
        cookieHeader: String?
    ): List<SourceVideoItem> = withContext(Dispatchers.IO) {
        val secUid = accountIdentifier.trim().substringAfterLast("/").substringBefore("?")
        if (!Regex("""[A-Za-z0-9._~-]{12,256}""").matches(secUid)) {
            throw SourceUnavailableException("Mã hồ sơ Douyin không hợp lệ. Hãy dùng sec_uid hoặc đường dẫn trang cá nhân.")
        }
        val cookie = cookieHeader.orEmpty()
        if (cookie.isBlank()) {
            throw SourceUnavailableException("Phiên đăng nhập Douyin chưa có cookie. Hãy đăng nhập lại trong Cài đặt.")
        }

        val payload = JSONObject().apply {
            put("sec_uid", secUid)
            put("cookie", cookie)
            put("since_timestamp", 0)
            put("max_items", MAX_VIDEOS)
            put("cursor", "0")
        }
        val request = Request.Builder()
            .url(SCAN_API_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        val responseText = scanClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw SourceUnavailableException(
                    detail?.takeIf { it.isNotBlank() }
                        ?: "Máy chủ chưa quét được danh sách Douyin (HTTP ${response.code})."
                )
            }
            body
        }
        val result = runCatching { JSONObject(responseText) }.getOrElse {
            throw SourceUnavailableException("Máy chủ quét Douyin trả về dữ liệu không hợp lệ.")
        }
        if (result.optString("status") != "success") {
            throw SourceUnavailableException(
                result.optString("message").ifBlank { "Máy chủ không lấy được danh sách video Douyin." }
            )
        }
        val entries = result.optJSONArray("videos")
            ?: throw SourceUnavailableException("Máy chủ không trả danh sách video Douyin.")
        val videos = linkedMapOf<String, SourceVideoItem>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
            val url = item.optString("url").ifBlank { videoUrl(id) }
            val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" }
            val description = item.optString("description").takeIf { it.isNotBlank() && it != "null" }
            val publishedAt = item.optLong("published_at", 0L).takeIf { it > 0L }
            videos.putIfAbsent(id, SourceVideoItem(
                id = id,
                url = url,
                title = title,
                description = description,
                publishedAt = publishedAt
            ))
        }
        if (videos.isEmpty()) {
            throw SourceUnavailableException("Douyin không có video công khai hoặc máy chủ chưa đọc được danh sách.")
        }
        videos.values.toList()
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
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.douyin.com/")
                .build()

            val (resolvedUrl, html) = profileClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SourceUnavailableException("Douyin trả về HTTP ${response.code} khi mở trang cá nhân.")
                }
                response.request.url.toString() to response.body?.string().orEmpty()
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
                    // Douyin sometimes embeds escaped JSON inside HTML script data.
                    val pattern = Regex("""\\?["']${Regex.escape(key)}\\?["']\s*:\s*\\?["']([^"']{1,1000})\\?["']""", RegexOption.IGNORE_CASE)
                    val value = pattern.find(html)?.groupValues?.getOrNull(1)
                        ?.replace("\\\"", "\"")
                        ?.replace("\\/", "/")
                        ?.replace("\\u002F", "/")
                        ?.replace("\\u0026", "&")
                        ?.trim()
                    if (!value.isNullOrBlank() && value != "null") return value
                }
                return null
            }
            fun numberField(vararg keys: String): Long? {
                for (key in keys) {
                    val pattern = Regex("""["']${Regex.escape(key)}["']\s*:\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
                    val value = pattern.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    if (value != null) return value
                }
                return null
            }

            fun secUidFromUrl(value: String): String? {
                val uri = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return null
                val segments = uri.pathSegments
                val userIndex = segments.indexOf("user")
                val pathValue = userIndex.takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }
                val decodedPathValue = pathValue?.let { android.net.Uri.decode(it) }
                return decodedPathValue?.takeIf { Regex("""[A-Za-z0-9._~-]{12,256}""").matches(it) }
            }
            val secUid = stringField("sec_uid", "secUid", "sec_user_id")
                ?.let { android.net.Uri.decode(it).trim() }
                ?.takeIf { Regex("""[A-Za-z0-9._~-]{12,256}""").matches(it) }
                ?: secUidFromUrl(resolvedUrl)
                ?: secUidFromUrl(identifier)
                ?: Regex("""["'](?:sec_uid|secUid|sec_user_id)["']\s*:\s*["']([^"']{12,256})["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.getOrNull(1)
                    ?.let { android.net.Uri.decode(it).trim() }
                    ?.takeIf { Regex("""[A-Za-z0-9._~-]{12,256}""").matches(it) }
            if (secUid.isNullOrBlank() || !Regex("""[A-Za-z0-9._~-]{12,256}""").matches(secUid)) {
                throw SourceUnavailableException("Chưa lấy được mã hồ sơ Douyin (sec_uid). Hãy dùng link trang cá nhân, không dùng ID hiển thị.")
            }

            val pageTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)
                ?.replace(Regex("""<[^>]+>"""), "")
                ?.replace("&quot;", "\"")
                ?.replace("&amp;", "&")
                ?.substringBefore(" - 抖音")
                ?.removeSuffix("的抖音")
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.contains("登录") && !it.contains("抖音") }
            // Douyin may serve a profile shell without serialized nickname data.
            // In that case, ask the signed profile-feed scanner for the author object
            // attached to the creator's first public video.
            val feedProfile = if (stringField("nickname", "nick_name", "user_name", "display_name").isNullOrBlank() && pageTitle.isNullOrBlank()) {
                val scanPayload = JSONObject().apply {
                    put("sec_uid", secUid)
                    put("cookie", cookieHeader)
                    put("since_timestamp", 0)
                    put("max_items", 1)
                    put("cursor", "0")
                }
                val scanRequest = Request.Builder()
                    .url(SCAN_API_URL)
                    .post(scanPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()
                val scanText = scanClient.newCall(scanRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                        throw SourceUnavailableException(
                            detail?.takeIf { it.isNotBlank() }
                                ?: "Máy chủ chưa lấy được thông tin tác giả Douyin (HTTP ${response.code})."
                        )
                    }
                    body
                }
                val scanJson = runCatching { JSONObject(scanText) }.getOrNull()
                    ?: throw SourceUnavailableException("Máy chủ trả dữ liệu hồ sơ Douyin không hợp lệ.")
                scanJson.optJSONObject("profile")
            } else null

            val nickname = stringField("nickname", "nick_name", "user_name", "display_name")
                ?: pageTitle
                ?: feedProfile?.optString("nickname")?.takeIf { it.isNotBlank() && it != "null" }
                ?: feedProfile?.optString("unique_id")?.takeIf { it.isNotBlank() && it != "null" }
                ?: throw SourceUnavailableException("Đã mở được hồ sơ nhưng cả trang hồ sơ và danh sách video đều không cung cấp tên tác giả.")
            val avatar = stringField("avatar_larger", "avatar_url", "avatar_thumb", "avatar")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: feedProfile?.optString("avatar_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            SourceProfileInfo(
                displayName = nickname,
                identifier = secUid,
                description = stringField("signature", "desc", "description")
                    ?: feedProfile?.optString("signature")?.takeIf { it.isNotBlank() && it != "null" },
                avatarUrl = avatar,
                videoCount = numberField("aweme_count", "video_count")?.toInt()
                    ?: feedProfile?.optLong("aweme_count")?.takeIf { it > 0L }?.toInt(),
                followerCount = numberField("follower_count", "followerCount", "mplatform_followers_count")
                    ?: feedProfile?.optLong("follower_count")?.takeIf { it > 0L },
                followingCount = numberField("following_count", "followingCount")
                    ?: feedProfile?.optLong("following_count")?.takeIf { it > 0L }
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
