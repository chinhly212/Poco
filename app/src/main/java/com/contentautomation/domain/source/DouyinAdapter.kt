package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
        const val PAGE_SIZE = 20
        const val MAX_VIDEOS = 600
        const val MAX_PAGES = MAX_VIDEOS / PAGE_SIZE
        const val PAGE_DELAY_MS = 900L
        const val USER_POST_API = "https://www.douyin.com/aweme/v1/web/aweme/post/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
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
        if (!secUid.startsWith("MS4wLjAB")) {
            throw SourceUnavailableException("Mã hồ sơ Douyin không hợp lệ. Cần sec_uid dạng MS4wLjAB…")
        }
        val cookie = cookieHeader.orEmpty()
        if (cookie.isBlank()) {
            throw SourceUnavailableException("Phiên đăng nhập Douyin chưa có cookie. Hãy đăng nhập lại trong Cài đặt.")
        }

        val cookieMap = cookie.split(";").mapNotNull { part ->
            val pair = part.trim().split("=", limit = 2)
            if (pair.size != 2 || pair[0].isBlank()) null else pair[0].trim() to pair[1].trim()
        }.toMap()
        val msToken = cookieMap["msToken"].orEmpty()
        val uifid = cookieMap["UIFID"] ?: cookieMap["UIFID_TEMP"]

        val collected = linkedMapOf<String, SourceVideoItem>()
        var maxCursor = "0"
        var hasMore = true

        for (page in 0 until MAX_PAGES) {
            if (!hasMore || collected.size >= MAX_VIDEOS) break
            val url = USER_POST_API.toHttpUrl().newBuilder()
                .addQueryParameter("device_platform", "webapp")
                .addQueryParameter("aid", "6383")
                .addQueryParameter("channel", "channel_pc_web")
                .addQueryParameter("version_code", "190600")
                .addQueryParameter("version_name", "19.6.0")
                .addQueryParameter("pc_client_type", "1")
                .addQueryParameter("cookie_enabled", "true")
                .addQueryParameter("browser_language", "zh-CN")
                .addQueryParameter("browser_platform", "Win32")
                .addQueryParameter("browser_name", "Chrome")
                .addQueryParameter("browser_version", "131.0.0.0")
                .addQueryParameter("browser_online", "true")
                .addQueryParameter("engine_name", "Blink")
                .addQueryParameter("os_name", "Windows")
                .addQueryParameter("platform", "PC")
                .addQueryParameter("screen_width", "1920")
                .addQueryParameter("screen_height", "1080")
                .addQueryParameter("sec_user_id", secUid)
                .addQueryParameter("count", PAGE_SIZE.toString())
                .addQueryParameter("max_cursor", maxCursor)
                .addQueryParameter("locate_query", "false")
                .addQueryParameter("publish_video_strategy_type", "2")
                .apply { if (msToken.isNotBlank()) addQueryParameter("msToken", msToken) }
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", profileUrl(secUid))
                .header("Origin", "https://www.douyin.com")
                .header("Cookie", cookie)
                .header("x-tt-argus", "1")
            if (!uifid.isNullOrBlank()) requestBuilder.header("uifid", uifid)

            val responseText = profileClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SourceUnavailableException("Douyin API trả HTTP ${response.code}. ${body.take(180)}")
                }
                body
            }
            val data = runCatching { JSONObject(responseText) }.getOrElse {
                throw SourceUnavailableException("Douyin API không trả JSON hợp lệ ở trang ${page + 1}.")
            }
            val statusCode = data.optInt("status_code", 0)
            if (statusCode != 0) {
                val message = data.optString("status_msg").ifBlank { data.optString("message") }
                throw SourceUnavailableException(
                    "Douyin từ chối API danh sách (status_code=$statusCode)${if (message.isNotBlank()) ": $message" else ""}. Phiên đăng nhập hoặc chữ ký bảo vệ của Douyin có thể cần làm mới."
                )
            }

            val entries = data.optJSONArray("aweme_list")
                ?: throw SourceUnavailableException("Douyin không trả trường aweme_list ở trang ${page + 1}.")
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val id = item.optString("aweme_id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val seconds = item.optLong("create_time", 0L)
                val desc = item.optString("desc").takeIf { it.isNotBlank() && it != "null" }
                collected.putIfAbsent(id, SourceVideoItem(
                    id = id,
                    url = videoUrl(id),
                    title = desc,
                    description = desc,
                    publishedAt = seconds.takeIf { it > 0L }?.times(1000L)
                ))
            }

            hasMore = data.optInt("has_more", 0) == 1
            val nextCursor = data.optString("max_cursor").takeIf { it.isNotBlank() && it != "null" }
            if (nextCursor == null || nextCursor == maxCursor) hasMore = false
            else maxCursor = nextCursor

            if (hasMore && collected.size < MAX_VIDEOS) delay(PAGE_DELAY_MS)
        }

        if (collected.isEmpty()) {
            throw SourceUnavailableException("Douyin đã phản hồi nhưng không có video công khai trong danh sách này.")
        }
        collected.values.take(MAX_VIDEOS)
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
                    val pattern = Regex("""["']${Regex.escape(key)}["']\s*:\s*["']([^"']{1,1000})["']""", RegexOption.IGNORE_CASE)
                    val value = pattern.find(html)?.groupValues?.getOrNull(1)
                        ?.replace("\\u002F", "/")?.replace("\\u0026", "&")?.trim()
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

            val pathId = finalUri.pathSegments.let { segments ->
                segments.indexOf("user").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }
            }
            val secUid = stringField("sec_uid", "secUid", "sec_user_id")
                ?: Regex("""/user/(MS4wLjAB[A-Za-z0-9_-]+)""").find(resolvedUrl)?.groupValues?.getOrNull(1)
                ?: pathId?.takeIf { it.startsWith("MS4wLjAB") }
            if (secUid.isNullOrBlank() || !secUid.startsWith("MS4wLjAB")) {
                throw SourceUnavailableException("Chưa lấy được mã hồ sơ Douyin (sec_uid). Hãy dùng link trang cá nhân, không dùng ID hiển thị.")
            }

            val nickname = stringField("nickname", "user_name", "display_name")
                ?: throw SourceUnavailableException("Đã mở được hồ sơ nhưng Douyin không trả tên tài khoản.")
            val avatar = stringField("avatar_larger", "avatar_url", "avatar_thumb", "avatar")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            SourceProfileInfo(
                displayName = nickname,
                identifier = secUid,
                description = stringField("signature", "desc", "description"),
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
