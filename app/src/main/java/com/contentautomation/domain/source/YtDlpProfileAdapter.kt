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

abstract class YtDlpProfileAdapter : SourceAdapter {
    protected abstract fun profileUrl(identifier: String): String
    protected abstract fun videoUrl(videoId: String): String

    private fun YoutubeDLRequest.addSession(cookieHeader: String?) {
        if (!cookieHeader.isNullOrBlank()) addOption("--add-headers", "Cookie: $cookieHeader")
    }

    suspend fun verifyProfile(identifier: String, cookieHeader: String): SourceProfileInfo =
        withContext(Dispatchers.IO) {
            require(identifier.isNotBlank()) { "ID tài khoản không được để trống." }
            require(cookieHeader.isNotBlank()) { "Chưa đăng nhập nền tảng. Hãy đăng nhập trước." }
            try {
                val output = YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(profileUrl(identifier)).apply {
                        addSession(cookieHeader)
                        addOption("--flat-playlist")
                        addOption("--dump-single-json")
                        addOption("--playlist-end", "1")
                        addOption("--skip-download")
                        addOption("--ignore-errors")
                        addOption("--no-warnings")
                    }
                ).out
                val root = JSONObject(output.trim())
                val entries = root.optJSONArray("entries")
                val title = firstText(root, "uploader", "channel", "creator", "title")
                val resolvedId = firstText(root, "channel_id", "uploader_id", "id") ?: identifier
                val avatar = firstText(root, "thumbnail", "avatar", "channel_thumbnail")
                val description = firstText(root, "description", "channel_description", "bio")
                val count = root.optInt("playlist_count", -1).takeIf { it >= 0 }
                    ?: root.optInt("video_count", -1).takeIf { it >= 0 }
                    ?: entries?.length()
                val hasEvidence = !title.isNullOrBlank() ||
                    !firstText(root, "channel_id", "uploader_id").isNullOrBlank() ||
                    (entries != null && entries.length() > 0)
                if (!hasEvidence) {
                    throw IllegalStateException("Trang không trả thông tin hồ sơ. ID có thể sai, phiên đăng nhập hết hạn hoặc nền tảng yêu cầu xác minh.")
                }
                SourceProfileInfo(title ?: identifier, resolvedId, description, avatar, count)
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
                    addSession(cookieHeader)
                    addOption("--flat-playlist")
                    addOption("--dump-single-json")
                    addOption("--playlist-end", "30")
                    addOption("--skip-download")
                    addOption("--ignore-errors")
                    addOption("--no-warnings")
                }
                val output = YoutubeDL.getInstance().execute(request).out
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
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(uploadDate)?.time
        }.getOrNull()
    }
}