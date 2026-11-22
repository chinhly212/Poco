package com.contentautomation.domain.source

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.io.File

abstract class YtDlpProfileAdapter(private val cookieDirectory: File? = null) : SourceAdapter {
    protected abstract fun profileUrl(identifier: String): String
    protected abstract fun videoUrl(videoId: String): String

    private fun executeWithSession(request: YoutubeDLRequest, cookieHeader: String?): String {
        if (cookieHeader.isNullOrBlank()) return YoutubeDL.getInstance().execute(request).out
        val directory = cookieDirectory ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Không thể tạo thư mục cookie tạm an toàn.")
        }
        val cookieFile = File.createTempFile("muchi-ytdlp-", ".cookies", directory)
        try {
            val lines = cookieHeader.split(";").mapNotNull { part ->
                val pair = part.trim().split("=", limit = 2)
                if (pair.size != 2 || pair[0].isBlank()) null
                else ".douyin.com\tTRUE\\t/\\tTRUE\\t0\\t${pair[0].trim()}\\t${pair[1].trim()}"
            }
            if (lines.isEmpty()) throw IllegalArgumentException("Phiên đăng nhập Douyin không có cookie hợp lệ.")
            cookieFile.writeText("# Netscape HTTP Cookie File\n" + lines.joinToString("\\n"), Charsets.UTF_8)
            request.addOption("--cookies", cookieFile.absolutePath)
            return YoutubeDL.getInstance().execute(request).out
        } finally {
            cookieFile.delete()
        }
    }

    open suspend fun verifyProfile(identifier: String, cookieHeader: String): SourceProfileInfo =
        withContext(Dispatchers.IO) {
            require(identifier.isNotBlank()) { "ID tài khoản không được để trống." }
            require(cookieHeader.isNotBlank()) { "Chưa đăng nhập nền tảng. Hãy đăng nhập trước." }
            try {
                // Do not send profile URLs to yt-dlp: many Douyin profile identifiers
                // are web handles rather than extractor-supported sec_uid values.
                // Read the official profile page directly and inspect its embedded state.
                val url = profileUrl(identifier)
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Cookie", cookieHeader)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
                val html = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Trang hồ sơ trả về HTTP ${response.code}.")
                        }
                        response.body?.string().orEmpty()
                    }
                if (html.isBlank()) throw IllegalStateException("Trang hồ sơ trả về nội dung rỗng.")
                fun findValue(vararg keys: String): String? {
                    for (key in keys) {
                        val escaped = Regex.escape(key)
                        val match = Regex("[\\\"]?$escaped[\\\"]?\\s*:\\s*[\\\"]([^\\\"]{1,500})[\\\"]", RegexOption.IGNORE_CASE)
                            .find(html) ?: continue
                        val value = match.groupValues[1]
                            .replace("\\\\u002F", "/")
                            .replace("\\\\u0026", "&")
                            .trim()
                        if (value.isNotBlank() && value != "null") return value
                    }
                    return null
                }
                val nickname = findValue("nickname", "user_name", "name", "author_name")
                val resolvedId = findValue("sec_uid", "secUid", "user_id", "userId", "uid")
                val avatar = findValue("avatar_larger", "avatar_url", "avatar")
                val description = findValue("signature", "desc", "description")
                val hasProfileEvidence = !resolvedId.isNullOrBlank() || !nickname.isNullOrBlank()
                if (!hasProfileEvidence) {
                    val loginRequired = html.contains("login", ignoreCase = true) ||
                        html.contains("passport", ignoreCase = true)
                    throw IllegalStateException(
                        if (loginRequired) "Trang chưa cung cấp dữ liệu hồ sơ. Phiên đăng nhập có thể chưa được lưu hoặc nền tảng yêu cầu xác minh."
                        else "Không tìm thấy dữ liệu hồ sơ trong trang. Hãy nhập ID hồ sơ chính xác, không nhập tên hiển thị."
                    )
                }
                SourceProfileInfo(
                    displayName = nickname ?: identifier,
                    identifier = resolvedId ?: identifier,
                    description = description,
                    avatarUrl = avatar,
                    videoCount = findValue("aweme_count", "video_count")?.toIntOrNull(),
                    followerCount = findValue("follower_count", "followerCount", "fans")?.toLongOrNull()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SourceUnavailableException("Không xác minh được hồ sơ: ${e.message ?: "Lỗi không xác định"}", e)
            }
        }

    override suspend fun getLatestVideos(accountIdentifier: String, cookieHeader: String?): List<SourceVideoItem> =
        withContext(Dispatchers.IO) {
            require(accountIdentifier.isNotBlank()) { "ID tài khoản không được để trống." }
            try {
                val request = YoutubeDLRequest(profileUrl(accountIdentifier)).apply {
                    addOption("--flat-playlist")
                    addOption("--dump-single-json")
                    addOption("--playlist-end", "100")
                    addOption("--skip-download")
                    addOption("--ignore-errors")
                    addOption("--no-warnings")
                }
                val output = executeWithSession(request, cookieHeader)
                val root = JSONObject(output.trim())
                val entries = root.optJSONArray("entries")
                val sourceEntries = entries ?: JSONArray().put(root)
                val result = ArrayList<SourceVideoItem>(sourceEntries.length())
                for (index in 0 until sourceEntries.length()) {
                    val item = sourceEntries.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val pageUrl = item.optString("webpage_url")
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: item.optString("original_url")
                            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: videoUrl(id)
                    result += SourceVideoItem(
                        id = id,
                        url = pageUrl,
                        title = item.optString("title").takeIf { it.isNotBlank() && it != "null" },
                        description = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                        publishedAt = readTimestamp(item)
                    )
                }
                result.distinctBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SourceUnavailableException("Không thể quét danh sách video bằng yt-dlp: ${e.message}", e)
            }
        }

    override suspend fun getVideoMetadata(videoId: String): SourceVideoItem? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            try {
                val item = JSONObject(YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(videoUrl(videoId)).apply {
                        addOption("--dump-single-json")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                    }
                ).out.trim())
                val id = item.optString("id").ifBlank { videoId }
                SourceVideoItem(
                    id = id,
                    url = item.optString("webpage_url").ifBlank { videoUrl(id) },
                    title = item.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    description = item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    publishedAt = readTimestamp(item)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SourceUnavailableException("Không lấy được thông tin video: ${e.message}", e)
            }
        }

    override suspend fun getDownloadUrl(videoId: String): String? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            try {
                val item = JSONObject(YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(videoUrl(videoId)).apply {
                        addOption("--dump-single-json")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                    }
                ).out.trim())
                item.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    private fun firstText(json: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            json.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }

    private fun readTimestamp(item: JSONObject): Long? {
        val seconds = item.optLong("timestamp", 0L).takeIf { it > 0L }
            ?: item.optLong("release_timestamp", 0L).takeIf { it > 0L }
        if (seconds != null) return seconds * 1000L
        val uploadDate = item.optString("upload_date")
        if (uploadDate.length != 8) return null
        return runCatching {
            SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }.parse(uploadDate)?.time
        }.getOrNull()
    }
}