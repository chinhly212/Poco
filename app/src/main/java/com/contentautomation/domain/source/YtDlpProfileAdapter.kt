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

    override suspend fun getLatestVideos(accountIdentifier: String): List<SourceVideoItem> =
        withContext(Dispatchers.IO) {
            require(accountIdentifier.isNotBlank()) { "ID tài khoản không được để trống." }
            try {
                val request = YoutubeDLRequest(profileUrl(accountIdentifier)).apply {
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
                    val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                        ?: continue
                    val pageUrl = item.optString("webpage_url")
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: item.optString("original_url")
                            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: videoUrl(id)
                    val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    val description = item.optString("description")
                        .takeIf { it.isNotBlank() && it != "null" }
                    result += SourceVideoItem(
                        id = id,
                        url = pageUrl,
                        title = title,
                        description = description,
                        publishedAt = readTimestamp(item)
                    )
                }

                result.distinctBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SourceUnavailableException(
                    "Không thể quét danh sách video bằng yt-dlp: ${e.message}",
                    e
                )
            }
        }

    override suspend fun getVideoMetadata(videoId: String): SourceVideoItem? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            try {
                val item = JSONObject(
                    YoutubeDL.getInstance().execute(
                        YoutubeDLRequest(videoUrl(videoId)).apply {
                            addOption("--dump-single-json")
                            addOption("--skip-download")
                            addOption("--no-warnings")
                        }
                    ).out.trim()
                )
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
                val item = JSONObject(
                    YoutubeDL.getInstance().execute(
                        YoutubeDLRequest(videoUrl(videoId)).apply {
                            addOption("--dump-single-json")
                            addOption("--skip-download")
                            addOption("--no-warnings")
                        }
                    ).out.trim()
                )
                item.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    private fun readTimestamp(item: JSONObject): Long? {
        val seconds = item.optLong("timestamp", 0L)
            .takeIf { it > 0L }
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
