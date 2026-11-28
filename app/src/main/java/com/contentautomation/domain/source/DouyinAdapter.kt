package com.contentautomation.domain.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.io.File

class DouyinAdapter(cookieDirectory: java.io.File? = null) : YtDlpProfileAdapter(cookieDirectory) {

    private val cookieDirectoryPath = cookieDirectory ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_SCAN_PAGES = 100
        const val PAGE_DELAY_MS = 900L
    }

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
        val cookie = cookieHeader.orEmpty()
        if (cookie.isBlank()) {
            throw SourceUnavailableException("Phiên đăng nhập Douyin chưa có cookie. Hãy đăng nhập lại trong Cài đặt.")
        }

        val directory = cookieDirectoryPath
        if (!directory.exists() && !directory.mkdirs()) {
            throw SourceUnavailableException("Không thể tạo thư mục tạm để xử lý phiên Douyin.")
        }
        val cookieFile = File.createTempFile("muchi-douyin-", ".cookies", directory)
        try {
            val cookieLines = cookie.split(";").mapNotNull { part ->
                val pair = part.trim().split("=", limit = 2)
                if (pair.size != 2 || pair[0].isBlank()) null
                else ".douyin.com\tTRUE\t/\tTRUE\t0\t${pair[0].trim()}\t${pair[1].trim()}"
            }
            if (cookieLines.isEmpty()) {
                throw SourceUnavailableException("Cookie Douyin không hợp lệ. Hãy đăng nhập lại.")
            }
            cookieFile.writeText("# Netscape HTTP Cookie File\n" + cookieLines.joinToString("\n"), Charsets.UTF_8)

            val request = YoutubeDLRequest(profileUrl(accountIdentifier)).apply {
                addOption("--cookies", cookieFile.absolutePath)
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--playlist-end", "600")
                addOption("--skip-download")
                addOption("--ignore-errors")
                addOption("--force-ipv4")
                addOption("--socket-timeout", "30")
                addOption("--retries", "3")
                addOption("--extractor-retries", "3")
                addOption("--retry-sleep", "http:1:3")
                addOption("--no-warnings")
            }

            val output = YoutubeDL.getInstance().execute(request).out.trim()
            if (output.isBlank()) {
                throw SourceUnavailableException("Douyin không trả dữ liệu danh sách video cho bộ quét trên điện thoại.")
            }
            val root = runCatching { JSONObject(output) }.getOrElse {
                throw SourceUnavailableException("Dữ liệu Douyin trả về không phải JSON hợp lệ: ${it.message}")
            }
            val entries = root.optJSONArray("entries")
            val sourceEntries = entries ?: org.json.JSONArray().put(root)
            val collected = linkedMapOf<String, SourceVideoItem>()

            for (index in 0 until sourceEntries.length()) {
                val item = sourceEntries.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val pageUrl = item.optString("webpage_url")
                    .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: item.optString("original_url")
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: item.optString("url")
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: videoUrl(id)
                val seconds = item.optLong("timestamp", 0L)
                val publishedAt = when {
                    seconds > 10_000_000_000L -> seconds
                    seconds > 0L -> seconds * 1000L
                    else -> null
                }
                collected.putIfAbsent(id, SourceVideoItem(
                    id = id,
                    url = pageUrl,
                    title = item.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    description = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    publishedAt = publishedAt
                ))
            }

            if (collected.isEmpty()) {
                throw SourceUnavailableException(
                    "Bộ quét trên điện thoại đã mở hồ sơ nhưng không lấy được video. Douyin có thể đã thay đổi cơ chế xác minh; cần kiểm tra phiên đăng nhập hoặc cập nhật bộ giải mã."
                )
            }
            collected.values.toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SourceUnavailableException("Không thể quét video Douyin trực tiếp trên điện thoại: ${e.message ?: "lỗi không xác định"}", e)
        } finally {
            cookieFile.delete()
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
