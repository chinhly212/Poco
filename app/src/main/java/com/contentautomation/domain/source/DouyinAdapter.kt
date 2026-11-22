package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DouyinAdapter : YtDlpProfileAdapter() {

    private val profileClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun profileUrl(identifier: String): String =
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) identifier
        else "https://www.douyin.com/user/$identifier"

    override fun videoUrl(videoId: String): String =
        "https://www.douyin.com/video/$videoId"

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
